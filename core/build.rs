use std::env;
use std::path::PathBuf;

fn main() {
    uniffi::generate_scaffolding("src/ledger.udl").unwrap();

    let manifest = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    let llama_src = manifest.parent().unwrap().join("third_party/llama.cpp");

    if !llama_src.join("CMakeLists.txt").exists() {
        println!(
            "cargo:warning=llama.cpp not found at {} — AI engine not built. \
             Run: git submodule add https://github.com/ggerganov/llama.cpp.git \
             third_party/llama.cpp",
            llama_src.display()
        );
        return;
    }

    let target = env::var("TARGET").unwrap_or_default();
    let ndk = env::var("ANDROID_NDK_HOME")
        .unwrap_or_else(|_| "C:/Users/dedek/AppData/Local/Android/Sdk/ndk/30.0.14904198".into());

    // ── Build llama.cpp as static library via CMake ───────────────────────────
    let mut cfg = cmake::Config::new(&llama_src);
    cfg.define("BUILD_SHARED_LIBS",    "OFF")
       .define("LLAMA_BUILD_TESTS",    "OFF")
       .define("LLAMA_BUILD_EXAMPLES", "OFF")
       .define("LLAMA_BUILD_SERVER",   "OFF")
       .define("GGML_METAL",           "OFF")
       .define("GGML_CUDA",            "OFF")
       .define("GGML_VULKAN",          "OFF")
       .define("GGML_OPENMP",          "OFF");

    if target.contains("android") {
        let abi = if target.starts_with("aarch64") {
            "arm64-v8a"
        } else if target.starts_with("armv7") || target.starts_with("arm-") {
            "armeabi-v7a"
        } else {
            "x86_64"
        };
        let toolchain = format!("{}/build/cmake/android.toolchain.cmake", ndk);
        cfg.define("CMAKE_TOOLCHAIN_FILE", &toolchain)
           .define("ANDROID_ABI",          abi)
           .define("ANDROID_PLATFORM",     "android-26")
           .define("ANDROID_STL",          "c++_shared");
    }

    let dst = cfg.build();

    // Add every subdirectory in the cmake output as a link search path
    let build_dir = dst.join("build");
    add_link_search_dirs(&build_dir);

    println!("cargo:rustc-link-lib=static=llama");
    println!("cargo:rustc-link-lib=static=ggml");

    // Newer llama.cpp splits ggml into sub-libraries
    for lib in &["ggml-base", "ggml-cpu", "ggml-alloc", "ggml-backend"] {
        println!("cargo:rustc-link-lib=static={}", lib);
    }

    if target.contains("android") {
        println!("cargo:rustc-link-lib=android");
        println!("cargo:rustc-link-lib=log");
        println!("cargo:rustc-link-lib=dylib=c++_shared");
    } else if target.contains("linux") {
        println!("cargo:rustc-link-lib=stdc++");
    }

    // ── Compile our thin C++ bridge with cc ───────────────────────────────────
    let bridge_src = manifest.join("src/llama_c/llama_simple.cpp");
    cc::Build::new()
        .cpp(true)
        .std("c++17")
        .opt_level(3)
        .file(&bridge_src)
        .include(llama_src.join("include"))
        .include(llama_src.join("ggml/include"))
        .compile("llama_simple");

    println!("cargo:rerun-if-changed=src/llama_c/llama_simple.h");
    println!("cargo:rerun-if-changed=src/llama_c/llama_simple.cpp");
    println!("cargo:rerun-if-changed={}", llama_src.join("include/llama.h").display());
}

fn add_link_search_dirs(dir: &std::path::Path) {
    if !dir.is_dir() { return; }
    println!("cargo:rustc-link-search=native={}", dir.display());
    if let Ok(entries) = std::fs::read_dir(dir) {
        for entry in entries.flatten() {
            if entry.path().is_dir() {
                add_link_search_dirs(&entry.path());
            }
        }
    }
}
