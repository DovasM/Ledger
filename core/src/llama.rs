use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_float, c_int, c_uint};
use std::sync::Mutex;

// ── C FFI ─────────────────────────────────────────────────────────────────────

#[repr(C)]
struct LlamaSimpleCtxOpaque {
    _unused: [u8; 0],
}

extern "C" {
    fn llama_simple_create(
        model_path: *const c_char,
        n_ctx: c_uint,
    ) -> *mut LlamaSimpleCtxOpaque;

    fn llama_simple_destroy(ctx: *mut LlamaSimpleCtxOpaque);

    fn llama_simple_generate(
        ctx: *mut LlamaSimpleCtxOpaque,
        prompt: *const c_char,
        n_predict: c_int,
        temperature: c_float,
    ) -> *mut c_char;

    fn llama_simple_free_str(s: *mut c_char);

    fn llama_simple_count_tokens(
        ctx: *mut LlamaSimpleCtxOpaque,
        prompt: *const c_char,
    ) -> c_int;

    fn llama_simple_system_info() -> *const c_char;

    fn llama_simple_last_prefill_ms(ctx: *mut LlamaSimpleCtxOpaque) -> i64;
    fn llama_simple_last_decode_ms(ctx: *mut LlamaSimpleCtxOpaque) -> i64;
}

// ── Safe Rust wrapper ─────────────────────────────────────────────────────────

struct LlamaInner {
    ptr: *mut LlamaSimpleCtxOpaque,
}

// llama.cpp context is single-threaded; we serialize access with Mutex below.
unsafe impl Send for LlamaInner {}

impl Drop for LlamaInner {
    fn drop(&mut self) {
        if !self.ptr.is_null() {
            unsafe { llama_simple_destroy(self.ptr) };
            self.ptr = std::ptr::null_mut();
        }
    }
}

// ── UniFFI interface ──────────────────────────────────────────────────────────

#[derive(Debug, thiserror::Error)]
pub enum LlamaError {
    #[error("Failed to load model: {0}")]
    LoadFailed(String),
    #[error("Generation failed")]
    GenerateFailed,
    #[error("Invalid string")]
    InvalidString,
}

pub struct LlamaEngine {
    inner: Mutex<LlamaInner>,
}

impl LlamaEngine {
    pub fn new(model_path: String, n_ctx: u32) -> Result<Self, LlamaError> {
        let path = CString::new(model_path.clone())
            .map_err(|_| LlamaError::LoadFailed("Invalid path".into()))?;

        let ptr = unsafe { llama_simple_create(path.as_ptr(), n_ctx) };
        if ptr.is_null() {
            return Err(LlamaError::LoadFailed(model_path));
        }

        Ok(LlamaEngine {
            inner: Mutex::new(LlamaInner { ptr }),
        })
    }

    pub fn generate(
        &self,
        prompt: String,
        n_predict: u32,
        temperature: f32,
    ) -> Result<String, LlamaError> {
        let cprompt = CString::new(prompt).map_err(|_| LlamaError::InvalidString)?;

        let guard = self.inner.lock().unwrap();
        let raw = unsafe {
            llama_simple_generate(
                guard.ptr,
                cprompt.as_ptr(),
                n_predict as c_int,
                temperature,
            )
        };

        if raw.is_null() {
            return Err(LlamaError::GenerateFailed);
        }

        let result = unsafe {
            let s = CStr::from_ptr(raw).to_string_lossy().into_owned();
            llama_simple_free_str(raw);
            s
        };

        Ok(result)
    }

    pub fn count_tokens(&self, prompt: String) -> i32 {
        let Ok(cprompt) = CString::new(prompt) else { return 0 };
        let guard = self.inner.lock().unwrap();
        unsafe { llama_simple_count_tokens(guard.ptr, cprompt.as_ptr()) }
    }

    pub fn system_info(&self) -> String {
        unsafe {
            let ptr = llama_simple_system_info();
            if ptr.is_null() { return String::new(); }
            CStr::from_ptr(ptr).to_string_lossy().into_owned()
        }
    }

    pub fn last_prefill_ms(&self) -> i64 {
        let guard = self.inner.lock().unwrap();
        unsafe { llama_simple_last_prefill_ms(guard.ptr) }
    }

    pub fn last_decode_ms(&self) -> i64 {
        let guard = self.inner.lock().unwrap();
        unsafe { llama_simple_last_decode_ms(guard.ptr) }
    }

    pub fn unload(&self) {
        // Dropping and reinitialising the pointer is the cleanest unload,
        // but since UniFFI wraps us in Arc we just clear the ptr here.
        let mut guard = self.inner.lock().unwrap();
        if !guard.ptr.is_null() {
            unsafe { llama_simple_destroy(guard.ptr) };
            guard.ptr = std::ptr::null_mut();
        }
    }
}
