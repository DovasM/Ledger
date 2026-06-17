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
    let vulkan_feature = cfg!(feature = "vulkan");

    let mut cfg = cmake::Config::new(&llama_src);
    cfg.define("BUILD_SHARED_LIBS",    "OFF")
       .define("LLAMA_BUILD_TESTS",    "OFF")
       .define("LLAMA_BUILD_EXAMPLES", "OFF")
       .define("LLAMA_BUILD_SERVER",   "OFF")
       .define("GGML_METAL",           "OFF")
       .define("GGML_CUDA",            "OFF")
       .define("GGML_VULKAN",          if vulkan_feature { "ON" } else { "OFF" })
       .define("GGML_OPENMP",          "OFF")
       // Disable dynamic backend loading: with DL=ON the Vulkan backend is a
       // separate .so that must be dlopen'd at runtime. DL=OFF links it statically.
       .define("GGML_BACKEND_DL",      "OFF");

    // When Vulkan is enabled, provide SPIRV-Headers and glslc locations.
    if vulkan_feature {
        // SPIRV-Headers cmake config dir (CMAKE_PREFIX_PATH is ignored by Android toolchain).
        let spirv_cfg_dir = manifest
            .parent().unwrap()
            .join("third_party/SPIRV-Headers-install/share/cmake/SPIRV-Headers");
        cfg.define("SPIRV-Headers_DIR", spirv_cfg_dir.to_str().unwrap());

        // glslc from Android NDK shader-tools (compiles GLSL → SPIR-V at build time)
        let glslc_path = format!("{}/shader-tools/windows-x86_64/glslc.exe", ndk);
        if std::path::Path::new(&glslc_path).exists() {
            cfg.define("Vulkan_GLSLC_EXECUTABLE", &glslc_path);
            println!("cargo:warning=Using glslc: {}", glslc_path);
        } else {
            println!("cargo:warning=glslc not found at {}", glslc_path);
        }
    }

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

        // Enable ARMv8.2-a dot-product SIMD for arm64 — supported on all
        // Snapdragon 845+ / Exynos 9820+ (i.e. every device from ~2018).
        // sdot/usdot instructions give ~2x throughput for Q4 matrix multiply.
        if target.starts_with("aarch64") {
            let mut cflags   = "-march=armv8.2-a+dotprod+fp16".to_string();
            let mut cxxflags = "-march=armv8.2-a+dotprod+fp16".to_string();
            if vulkan_feature {
                // NDK sysroot has vulkan.h but not vulkan.hpp or spirv.hpp.
                // Provide both from our third_party clones.
                let root = manifest.parent().unwrap();
                for sub in &["third_party/Vulkan-Headers/include",
                             "third_party/SPIRV-Headers/include"] {
                    let inc = format!(" -I{}", root.join(sub).to_str().unwrap().replace('\\', "/"));
                    cflags.push_str(&inc);
                    cxxflags.push_str(&inc);
                }
            }
            cfg.define("CMAKE_C_FLAGS",   &cflags)
               .define("CMAKE_CXX_FLAGS", &cxxflags);
        }
    }

    // cmake crate doesn't forward CMAKE_MAKE_PROGRAM automatically — pass it explicitly
    if let Ok(ninja) = env::var("CMAKE_MAKE_PROGRAM") {
        cfg.define("CMAKE_MAKE_PROGRAM", &ninja);
    }

    let dst = cfg.build_target("llama").build();

    // Add every subdirectory in the cmake output as a link search path
    let build_dir = dst.join("build");
    add_link_search_dirs(&build_dir);

    println!("cargo:rustc-link-lib=static=llama");
    // ggml-alloc and ggml-backend are merged into ggml-base in this version
    for lib in &["ggml-base", "ggml-cpu", "ggml"] {
        println!("cargo:rustc-link-lib=static={}", lib);
    }

    if target.contains("android") {
        println!("cargo:rustc-link-lib=android");
        println!("cargo:rustc-link-lib=log");
        println!("cargo:rustc-link-lib=dylib=c++_shared");
        if vulkan_feature {
            // ggml-vulkan.a contains ggml_backend_vk_reg; must be linked explicitly.
            println!("cargo:rustc-link-lib=static=ggml-vulkan");
            // libvulkan.so is a system library present on Android 7.0+ with Vulkan support.
            println!("cargo:rustc-link-lib=vulkan");
        }
    } else if target.contains("linux") {
        println!("cargo:rustc-link-lib=stdc++");
    }

    // ── Compile our thin C++ bridge with cc ───────────────────────────────────
    let bridge_src = manifest.join("src/llama_c/llama_simple.cpp");
    let mut bridge = cc::Build::new();
    bridge
        .cpp(true)
        .std("c++17")
        .opt_level(3)
        .file(&bridge_src)
        .include(llama_src.join("include"))
        .include(llama_src.join("ggml/include"));
    // The bridge guards its GPU-offload path behind #ifdef GGML_VULKAN. The CMake
    // build above defines this for llama.cpp itself, but `cc` compiles the bridge
    // separately — so we must define it here too, or the bridge silently takes its
    // CPU-only #else branch and never attempts GPU offload.
    if vulkan_feature {
        bridge.define("GGML_VULKAN", None);
    }
    if target.starts_with("aarch64") && target.contains("android") {
        bridge.flag("-march=armv8.2-a+dotprod+fp16");
    }
    bridge.compile("llama_simple");

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
