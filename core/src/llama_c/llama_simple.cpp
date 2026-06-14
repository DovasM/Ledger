// Thin C wrapper around llama.cpp so Rust FFI only needs simple types.
// Compiled by Rust's build.rs via the `cc` crate.
#include "llama_simple.h"
#include "llama.h"

#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#ifdef __ANDROID__
#include <android/log.h>
#define LS_LOG(...) __android_log_print(ANDROID_LOG_DEBUG, "LlamaSimple", __VA_ARGS__)
#else
#define LS_LOG(...) fprintf(stderr, __VA_ARGS__)
#endif

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

struct LlamaSimpleCtx {
    llama_model*   model = nullptr;
    llama_context* ctx   = nullptr;
    llama_batch    batch;
    bool           batch_ok    = false;
    int64_t        prefill_ms  = 0;
    int64_t        decode_ms   = 0;
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

LlamaSimpleCtx* llama_simple_create(const char* model_path, uint32_t n_ctx) {
    llama_backend_init();
    llama_log_set([](ggml_log_level, const char*, void*) {}, nullptr);

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;

    llama_model* model = llama_model_load_from_file(model_path, mp);
    if (!model) return nullptr;

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx           = n_ctx;
    cp.n_batch         = n_ctx;  // must be >= max prompt length to avoid abort()
    cp.n_ubatch        = 512;   // internal micro-batch; affects memory bandwidth efficiency
    cp.n_threads       = 4;
    cp.n_threads_batch = 4;

    llama_context* ctx = llama_init_from_model(model, cp);
    if (!ctx) { llama_model_free(model); return nullptr; }

    auto* s    = new LlamaSimpleCtx();
    s->model   = model;
    s->ctx     = ctx;
    s->batch   = llama_batch_init((int32_t)n_ctx, 0, 1);
    s->batch_ok = true;

    LS_LOG("Context params: n_ctx=%u n_batch=%u n_ubatch=%u n_threads=%d n_threads_batch=%d",
           n_ctx, cp.n_batch, cp.n_ubatch, cp.n_threads, cp.n_threads_batch);
    LS_LOG("CPU freqs at load — cpu0:%lldMHz cpu4:%lldMHz cpu7:%lldMHz | temp:%.1f°C",
           cpu_freq_mhz(0), cpu_freq_mhz(4), cpu_freq_mhz(7), cpu_temp_c());

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

char* llama_simple_generate(LlamaSimpleCtx* s,
                             const char*    prompt,
                             int32_t        n_predict,
                             float          temperature) {
    if (!s) return nullptr;

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
    if (llama_decode(s->ctx, s->batch)) return nullptr;
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
        if (llama_decode(s->ctx, s->batch)) break;
    }
    s->decode_ms = now_ms() - t_decode_start;
    int n_output = (int)result.size(); // approximate; real token count via count_tokens

    // ── Wall-clock summary + thermal snapshot ─────────────────────────────────
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
    return llama_print_system_info();
}

int64_t llama_simple_last_prefill_ms(LlamaSimpleCtx* s) {
    return s ? s->prefill_ms : 0;
}

int64_t llama_simple_last_decode_ms(LlamaSimpleCtx* s) {
    return s ? s->decode_ms : 0;
}
