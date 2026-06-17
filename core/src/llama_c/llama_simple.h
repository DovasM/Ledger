#pragma once
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Opaque context — owns model + context + batch allocation
typedef struct LlamaSimpleCtx LlamaSimpleCtx;

// Returns nullptr on failure
LlamaSimpleCtx* llama_simple_create(const char* model_path, uint32_t n_ctx);

void llama_simple_destroy(LlamaSimpleCtx* ctx);

// Generates text for the given prompt. Returns heap-allocated C string; free with llama_simple_free_str.
// Returns nullptr on error.
char* llama_simple_generate(LlamaSimpleCtx* ctx,
                             const char*    prompt,
                             int32_t        n_predict,
                             float          temperature);

void llama_simple_free_str(char* s);

// Returns number of prompt tokens (>= 0), or 0 on error.
int32_t llama_simple_count_tokens(LlamaSimpleCtx* ctx, const char* prompt);

// Returns llama_print_system_info() — static buffer, do NOT free.
const char* llama_simple_system_info(void);

// Timing from the last generate() call (milliseconds).
// prefill = time from first decode() call until first output token.
// decode  = time from first output token until last output token.
int64_t llama_simple_last_prefill_ms(LlamaSimpleCtx* ctx);
int64_t llama_simple_last_decode_ms(LlamaSimpleCtx* ctx);

#ifdef __cplusplus
}
#endif
