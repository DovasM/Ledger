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

#ifdef __cplusplus
}
#endif
