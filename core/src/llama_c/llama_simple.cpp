// Thin C wrapper around llama.cpp so Rust FFI only needs simple types.
// Compiled by Rust's build.rs via the `cc` crate.
#include "llama_simple.h"
#include "llama.h"

#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

struct LlamaSimpleCtx {
    llama_model*   model = nullptr;
    llama_context* ctx   = nullptr;
    llama_batch    batch;
    bool           batch_ok = false;
};

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

    llama_model* model = llama_load_model_from_file(model_path, mp);
    if (!model) return nullptr;

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx           = n_ctx;
    cp.n_threads       = 4;
    cp.n_threads_batch = 4;

    llama_context* ctx = llama_new_context_with_model(model, cp);
    if (!ctx) { llama_model_free(model); return nullptr; }

    auto* s    = new LlamaSimpleCtx();
    s->model   = model;
    s->ctx     = ctx;
    s->batch   = llama_batch_init((int32_t)n_ctx, 0, 1);
    s->batch_ok = true;
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

    // Tokenize
    int prompt_len = (int)strlen(prompt);
    std::vector<llama_token> tokens(prompt_len + 64);
    int n = llama_tokenize(s->model, prompt, prompt_len,
                            tokens.data(), (int)tokens.size(), true, true);
    if (n < 0) return nullptr;
    tokens.resize(n);

    llama_kv_cache_clear(s->ctx);

    s->batch.n_tokens = 0;
    for (int i = 0; i < n; i++) batch_add(s->batch, tokens[i], i, i == n - 1);
    if (llama_decode(s->ctx, s->batch)) return nullptr;

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

    for (int i = 0; i < n_predict; i++) {
        llama_token tok = llama_sampler_sample(smpl, s->ctx, -1);
        if (llama_token_is_eog(s->model, tok)) break;

        int plen = llama_token_to_piece(s->model, tok, piece, (int)sizeof(piece) - 1, 0, true);
        if (plen > 0) { piece[plen] = '\0'; result += piece; }

        s->batch.n_tokens = 0;
        batch_add(s->batch, tok, ncur++, true);
        if (llama_decode(s->ctx, s->batch)) break;
    }

    llama_sampler_free(smpl);

    char* out = (char*)malloc(result.size() + 1);
    if (out) memcpy(out, result.c_str(), result.size() + 1);
    return out;
}

void llama_simple_free_str(char* s) {
    free(s);
}
