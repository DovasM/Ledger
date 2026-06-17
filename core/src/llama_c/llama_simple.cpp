// Thin C wrapper around llama.cpp so Rust FFI only needs simple types.
// Compiled by Rust's build.rs via the `cc` crate.
#include "llama_simple.h"
#include "llama.h"
#include "ggml-backend.h"

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <exception>
#include <string>
#include <vector>

#ifdef __ANDROID__
#include <android/log.h>
#define LS_LOG(...) __android_log_print(ANDROID_LOG_DEBUG, "LlamaSimple", __VA_ARGS__)
#else
#define LS_LOG(...) fprintf(stderr, __VA_ARGS__)
#endif

// ── Diagnostics ───────────────────────────────────────────────────────────
// ggml / ggml-vulkan emit their most useful failure details to two streams that
// were both being thrown away: the ggml log callback (device list, driver
// version, feature selection, errors) was silenced by a no-op callback, and the
// exact "ggml_vulkan: Compute pipeline creation failed for <shader>" line is
// written to std::cerr, which Android discards. The helpers below restore both
// streams into logcat so we can see WHY the Adreno driver rejects the pipelines.

#ifdef __ANDROID__
#include <unistd.h>
#include <pthread.h>

// Reader thread: pump everything written to stderr into logcat, line by line.
static void* stderr_pump(void* arg) {
    int rfd = (int)(intptr_t)arg;
    char buf[512];
    std::string line;
    ssize_t n;
    while ((n = read(rfd, buf, sizeof(buf) - 1)) > 0) {
        line.append(buf, (size_t)n);
        size_t nl;
        while ((nl = line.find('\n')) != std::string::npos) {
            if (nl > 0)
                __android_log_print(ANDROID_LOG_WARN, "LlamaStderr", "%s",
                                    line.substr(0, nl).c_str());
            line.erase(0, nl + 1);
        }
    }
    return nullptr;
}

// Redirect the process stderr fd into logcat (once). Captures ggml-vulkan's
// "Compute pipeline creation failed for <shader>" message, our only clue to the
// exact shader the driver can't compile.
static void redirect_stderr_to_logcat() {
    static bool done = false;
    if (done) return;
    done = true;
    int pipefd[2];
    if (pipe(pipefd) != 0) return;
    dup2(pipefd[1], STDERR_FILENO);
    close(pipefd[1]);
    setvbuf(stderr, nullptr, _IONBF, 0);  // unbuffered → lines reach logcat at once
    pthread_t t;
    if (pthread_create(&t, nullptr, stderr_pump, (void*)(intptr_t)pipefd[0]) == 0)
        pthread_detach(t);
}
#else
static void redirect_stderr_to_logcat() {}
#endif

// Forward llama.cpp/ggml internal logs (Vulkan device enumeration, driver info,
// feature selection, errors) into logcat. Replaces the old no-op callback.
static void ggml_log_forward(ggml_log_level level, const char* text, void*) {
    if (!text || !*text) return;
#ifdef __ANDROID__
    int prio = (level == GGML_LOG_LEVEL_ERROR) ? ANDROID_LOG_ERROR
             : (level == GGML_LOG_LEVEL_WARN)  ? ANDROID_LOG_WARN
             :                                   ANDROID_LOG_DEBUG;
    __android_log_print(prio, "LlamaGGML", "%s", text);
#else
    (void)level;
    fprintf(stderr, "%s", text);
#endif
}

// Enumerate every ggml backend device — confirms whether the Vulkan GPU is
// actually registered and shows its name and driver-reported memory.
static void log_backend_devices() {
    size_t n = ggml_backend_dev_count();
    LS_LOG("ggml backend devices: %zu", n);
    for (size_t i = 0; i < n; i++) {
        ggml_backend_dev_t dev = ggml_backend_dev_get(i);
        if (!dev) continue;
        enum ggml_backend_dev_type type = ggml_backend_dev_type(dev);
        const char* tname = type == GGML_BACKEND_DEVICE_TYPE_CPU   ? "CPU"
                          : type == GGML_BACKEND_DEVICE_TYPE_GPU   ? "GPU"
                          : type == GGML_BACKEND_DEVICE_TYPE_IGPU  ? "IGPU"
                          : type == GGML_BACKEND_DEVICE_TYPE_ACCEL ? "ACCEL" : "?";
        size_t free_mem = 0, total_mem = 0;
        ggml_backend_dev_memory(dev, &free_mem, &total_mem);
        LS_LOG("  dev[%zu] name=%s type=%s desc=%s mem(free/total)=%zu/%zuMB",
               i, ggml_backend_dev_name(dev), tname,
               ggml_backend_dev_description(dev),
               free_mem >> 20, total_mem >> 20);
    }
}

// Read current frequency (kHz→MHz) for one CPU core; returns -1 on failure.
static int64_t cpu_freq_mhz(int core) {
    char path[128];
    snprintf(path, sizeof(path),
             "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq", core);
    FILE* f = fopen(path, "r");
    if (!f) return -1;
    int64_t khz = 0;
    fscanf(f, "%lld", &khz);
    fclose(f);
    return khz / 1000;
}

// Read CPU temperature from the first thermal zone (millidegrees → °C).
static float cpu_temp_c() {
    FILE* f = fopen("/sys/class/thermal/thermal_zone0/temp", "r");
    if (!f) return -1.0f;
    int t = 0;
    fscanf(f, "%d", &t);
    fclose(f);
    return t / 1000.0f;
}

// Tracks whether the current context is using GPU offload.
static bool g_using_gpu = false;

struct LlamaSimpleCtx {
    llama_model*   model      = nullptr;
    llama_context* ctx        = nullptr;
    llama_batch    batch;
    bool           batch_ok   = false;
    int64_t        prefill_ms = 0;
    int64_t        decode_ms  = 0;
    std::string    model_path;
    uint32_t       n_ctx_val  = 0;
};

static int64_t now_ms() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

static void batch_add(llama_batch& b, llama_token id, llama_pos pos, bool logits) {
    b.token   [b.n_tokens] = id;
    b.pos     [b.n_tokens] = pos;
    b.n_seq_id[b.n_tokens] = 1;
    b.seq_id  [b.n_tokens][0] = 0;
    b.logits  [b.n_tokens] = logits ? 1 : 0;
    b.n_tokens++;
}

// CPU-only reload after any backend exception.
// KEY: split_mode=NONE + main_gpu=-1 causes llama.cpp to call model->devices.clear(),
// so the context scheduler has NO Vulkan backend — Vulkan cannot interfere with CPU ops.
static bool reload_as_cpu(LlamaSimpleCtx* s) {
    LS_LOG("Exception caught — reloading as strict CPU-only.");
    if (s->ctx)     { llama_free(s->ctx);             s->ctx     = nullptr; }
    if (s->model)   { llama_model_free(s->model);     s->model   = nullptr; }
    if (s->batch_ok){ llama_batch_free(s->batch);     s->batch_ok = false; }

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    mp.split_mode   = LLAMA_SPLIT_MODE_NONE;
    mp.main_gpu     = -1;  // triggers model->devices.clear() → no GPU in scheduler
    s->model = llama_model_load_from_file(s->model_path.c_str(), mp);
    if (!s->model) { LS_LOG("CPU reload: model load failed."); return false; }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx           = s->n_ctx_val;
    cp.n_batch         = s->n_ctx_val;
    cp.n_ubatch        = 512;
    cp.n_threads       = 4;
    cp.n_threads_batch = 4;

    s->ctx = llama_init_from_model(s->model, cp);
    if (!s->ctx) {
        llama_model_free(s->model); s->model = nullptr;
        LS_LOG("CPU reload: context init failed."); return false;
    }

    s->batch    = llama_batch_init((int32_t)s->n_ctx_val, 0, 1);
    s->batch_ok = true;
    g_using_gpu = false;
    LS_LOG("CPU reload successful — Vulkan excluded from scheduler.");
    return true;
}

// Single-token decode to force the backend to compile its compute pipelines.
// On Adreno/Vulkan this is where createComputePipeline throws — running it at
// load time lets us catch the failure and fall back to CPU BEFORE any real
// scan, instead of stalling mid-inference. Also pre-faults the mmap'd weights
// into the page cache so the first real scan isn't I/O-bound. Throws on failure.
static bool warmup(LlamaSimpleCtx* s) {
    const llama_vocab* vocab = llama_model_get_vocab(s->model);
    llama_token bos = llama_vocab_bos(vocab);
    if (bos < 0) bos = 0;

    auto* mem = llama_get_memory(s->ctx);
    if (mem) llama_memory_clear(mem, true);

    s->batch.n_tokens = 0;
    batch_add(s->batch, bos, 0, true);
    return llama_decode(s->ctx, s->batch) == 0;  // may throw on Vulkan failure
}

LlamaSimpleCtx* llama_simple_create(const char* model_path, uint32_t n_ctx) {
    redirect_stderr_to_logcat();                // capture ggml-vulkan's std::cerr diagnostics
    llama_log_set(ggml_log_forward, nullptr);   // forward ggml/Vulkan logs to logcat
    // NOTE: GGML_VK_DISABLE_F16 was tried here as an Adreno workaround and ruled out —
    // the q4_k mat-vec kernel fails to compile in its f32-only form too (see memory
    // project-vulkan-blocked). The Qualcomm proprietary Adreno driver cannot compile
    // llama.cpp's quantized compute shaders; GPU offload always falls back to CPU below.
    llama_backend_init();
    log_backend_devices();                      // dump detected backends (is Vulkan GPU present?)

    llama_model_params mp = llama_model_default_params();
    llama_model* model = nullptr;
    g_using_gpu = false;

#ifdef GGML_VULKAN
    // Attempt GPU offload — offload all layers to Vulkan device.
    mp.n_gpu_layers = 99;
    LS_LOG("Attempting Vulkan GPU offload (n_gpu_layers=99)...");
    model = llama_model_load_from_file(model_path, mp);
    if (model) {
        g_using_gpu = true;
        LS_LOG("Model loaded with Vulkan GPU offload.");
    } else {
        // GPU load failed. Reload with split_mode=NONE + main_gpu=-1 so that
        // llama.cpp calls model->devices.clear() — this removes the Vulkan device
        // from the context scheduler, preventing Vulkan from being used for compute.
        LS_LOG("Vulkan GPU load failed — falling back to strict CPU-only.");
        mp.n_gpu_layers = 0;
        mp.split_mode   = LLAMA_SPLIT_MODE_NONE;
        mp.main_gpu     = -1;  // triggers model->devices.clear() → no GPU in scheduler
        model = llama_model_load_from_file(model_path, mp);
    }
#else
    // No Vulkan compiled in: strict CPU. split_mode=NONE + main_gpu=-1 ensures
    // model->devices is cleared even if some other GPU backend is registered.
    mp.n_gpu_layers = 0;
    mp.split_mode   = LLAMA_SPLIT_MODE_NONE;
    mp.main_gpu     = -1;
    model = llama_model_load_from_file(model_path, mp);
#endif

    if (!model) return nullptr;

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx           = n_ctx;
    cp.n_batch         = n_ctx;  // must be >= max prompt length to avoid abort()
    cp.n_ubatch        = 512;   // internal micro-batch; affects memory bandwidth efficiency
    cp.n_threads       = 4;
    cp.n_threads_batch = 4;

    llama_context* ctx = llama_init_from_model(model, cp);
    if (!ctx) { llama_model_free(model); return nullptr; }

    auto* s       = new LlamaSimpleCtx();
    s->model      = model;
    s->ctx        = ctx;
    s->batch      = llama_batch_init((int32_t)n_ctx, 0, 1);
    s->batch_ok   = true;
    s->model_path = model_path;
    s->n_ctx_val  = n_ctx;

    LS_LOG("Context params: n_ctx=%u n_batch=%u n_ubatch=%u n_threads=%d n_threads_batch=%d",
           n_ctx, cp.n_batch, cp.n_ubatch, cp.n_threads, cp.n_threads_batch);
    LS_LOG("CPU freqs at load — cpu0:%lldMHz cpu4:%lldMHz cpu7:%lldMHz | temp:%.1f°C",
           cpu_freq_mhz(0), cpu_freq_mhz(4), cpu_freq_mhz(7), cpu_temp_c());

    // Warm up at load time: compile compute pipelines + pre-fault weights now.
    // If the (Vulkan) backend can't build its pipelines, catch it here and
    // reload as CPU — so the first real scan never stalls or throws mid-way.
    LS_LOG("Warming up backend (g_using_gpu=%d)...", (int)g_using_gpu);
    try {
        if (!warmup(s)) {
            LS_LOG("Warmup decode returned error — reloading as CPU.");
            reload_as_cpu(s);
        } else {
            LS_LOG("Warmup OK — backend functional (g_using_gpu=%d).", (int)g_using_gpu);
        }
    } catch (const std::exception& e) {
        LS_LOG("Warmup threw (%s) — reloading as CPU.", e.what());
        reload_as_cpu(s);
    } catch (...) {
        LS_LOG("Warmup threw unknown exception — reloading as CPU.");
        reload_as_cpu(s);
    }

    return s;
}

void llama_simple_destroy(LlamaSimpleCtx* s) {
    if (!s) return;
    if (s->batch_ok) llama_batch_free(s->batch);
    if (s->ctx)      llama_free(s->ctx);
    if (s->model)    llama_model_free(s->model);
    llama_backend_free();
    delete s;
}

// Core generation logic. Throws on Vulkan/llama errors so caller can catch and retry.
static char* generate_impl(LlamaSimpleCtx* s, const char* prompt,
                            int32_t n_predict, float temperature) {
    const llama_vocab* vocab = llama_model_get_vocab(s->model);

    // Tokenize
    int prompt_len = (int)strlen(prompt);
    std::vector<llama_token> tokens(prompt_len + 64);
    int n = llama_tokenize(vocab, prompt, prompt_len,
                            tokens.data(), (int)tokens.size(), true, true);
    if (n < 0) return nullptr;

    // Truncate prompt to fit context window (leaves room for output tokens)
    int max_prompt = (int)llama_n_ctx(s->ctx) - 4;
    if (n > max_prompt) n = max_prompt;
    tokens.resize(n);

    auto* mem = llama_get_memory(s->ctx);
    if (mem) llama_memory_clear(mem, true);

    s->batch.n_tokens = 0;
    for (int i = 0; i < n; i++) batch_add(s->batch, tokens[i], i, i == n - 1);

    // ── Prefill: single decode() call processes entire prompt ─────────────────
    int64_t t_prefill_start = now_ms();
    if (llama_decode(s->ctx, s->batch)) return nullptr;  // may throw on Vulkan failure
    s->prefill_ms = now_ms() - t_prefill_start;

    // Sampler (per-call so temperature is respected)
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    if (temperature <= 0.01f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(42));
    }

    std::string result;
    char piece[256];
    int ncur = n;

    // ── Decode: one token at a time ───────────────────────────────────────────
    int64_t t_decode_start = now_ms();
    for (int i = 0; i < n_predict; i++) {
        llama_token tok = llama_sampler_sample(smpl, s->ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        int plen = llama_token_to_piece(vocab, tok, piece, (int)sizeof(piece) - 1, 0, true);
        if (plen > 0) { piece[plen] = '\0'; result += piece; }

        s->batch.n_tokens = 0;
        batch_add(s->batch, tok, ncur++, true);
        if (llama_decode(s->ctx, s->batch)) break;  // may throw on Vulkan failure
    }
    s->decode_ms = now_ms() - t_decode_start;

    double pf_tps = s->prefill_ms > 0 ? n * 1000.0 / s->prefill_ms : 0;
    LS_LOG("Wall: prefill=%lldms (%.1f tok/s) decode=%lldms",
           s->prefill_ms, pf_tps, s->decode_ms);
    LS_LOG("CPU freqs after gen — cpu0:%lldMHz cpu4:%lldMHz cpu7:%lldMHz | temp:%.1f°C",
           cpu_freq_mhz(0), cpu_freq_mhz(4), cpu_freq_mhz(7), cpu_temp_c());

    llama_sampler_free(smpl);

    char* out = (char*)malloc(result.size() + 1);
    if (out) memcpy(out, result.c_str(), result.size() + 1);
    return out;
}

char* llama_simple_generate(LlamaSimpleCtx* s,
                             const char*    prompt,
                             int32_t        n_predict,
                             float          temperature) {
    if (!s) return nullptr;

    // First attempt (may use Vulkan GPU, or CPU with Vulkan still registered).
    try {
        return generate_impl(s, prompt, n_predict, temperature);
    } catch (const std::exception& e) {
        LS_LOG("Exception during generate: %s", e.what());
    } catch (...) {
        LS_LOG("Unknown exception during generate.");
    }

    // Any exception (GPU or Vulkan-interfering-with-CPU): full backend reset + retry.
    if (!reload_as_cpu(s)) return nullptr;

    try {
        return generate_impl(s, prompt, n_predict, temperature);
    } catch (const std::exception& e) {
        LS_LOG("Exception during CPU fallback generate: %s", e.what());
        return nullptr;
    } catch (...) {
        LS_LOG("Unknown exception during CPU fallback generate.");
        return nullptr;
    }
}

void llama_simple_free_str(char* s) {
    free(s);
}

int32_t llama_simple_count_tokens(LlamaSimpleCtx* s, const char* prompt) {
    if (!s || !prompt) return 0;
    const llama_vocab* vocab = llama_model_get_vocab(s->model);
    int len = (int)strlen(prompt);
    std::vector<llama_token> tokens(len + 64);
    int n = llama_tokenize(vocab, prompt, len, tokens.data(), (int)tokens.size(), true, true);
    return n < 0 ? 0 : n;
}

const char* llama_simple_system_info(void) {
    static std::string info;
    const char* prefix = g_using_gpu ? "GPU_BACKEND=Vulkan | " : "GPU_BACKEND=CPU | ";
    info = std::string(prefix) + llama_print_system_info();
    return info.c_str();
}

int64_t llama_simple_last_prefill_ms(LlamaSimpleCtx* s) {
    return s ? s->prefill_ms : 0;
}

int64_t llama_simple_last_decode_ms(LlamaSimpleCtx* s) {
    return s ? s->decode_ms : 0;
}
