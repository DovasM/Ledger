# Vulkan GPU backend build - arm64-v8a only.
# Run from project root: .\build-rust-android-vulkan.ps1

$ErrorActionPreference = "Stop"

$NDK   = "C:\Users\dedek\AppData\Local\Android\Sdk\ndk\30.0.14904198"
$CMAKE = "C:\Users\dedek\AppData\Local\Android\Sdk\cmake\3.22.1\bin\cmake.exe"
$NINJA = "C:\Users\dedek\AppData\Local\Android\Sdk\cmake\3.22.1\bin\ninja.exe"

# Initialize MSVC environment so detect_host_compiler() in llama.cpp cmake finds cl.exe
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (-not (Test-Path $vswhere)) {
    $vswhere = "$env:ProgramFiles\Microsoft Visual Studio\Installer\vswhere.exe"
}
if (-not (Test-Path $vswhere)) {
    Write-Error "vswhere.exe not found - is MSVC Build Tools installed?"
    exit 1
}

$vsPath = & $vswhere -latest -products * -requires Microsoft.VisualCpp.Tools.HostX64.TargetX64 -property installationPath 2>&1 | Select-Object -First 1
if (-not $vsPath) {
    Write-Error "No MSVC HostX64/TargetX64 installation found."
    exit 1
}

$vcvarsall = "$vsPath\VC\Auxiliary\Build\vcvarsall.bat"
if (-not (Test-Path $vcvarsall)) {
    Write-Error "vcvarsall.bat not found at: $vcvarsall"
    exit 1
}

Write-Host "Importing MSVC environment from: $vcvarsall" -ForegroundColor Yellow

$tempBat = "$env:TEMP\vcvars_capture.bat"
$tempEnv = "$env:TEMP\vcvars_env.txt"
$vsInstallerDir = Split-Path $vswhere -Parent
"@echo off" | Set-Content $tempBat -Encoding Ascii
"set PATH=$vsInstallerDir;%PATH%" | Add-Content $tempBat -Encoding Ascii
"call `"$vcvarsall`" x64" | Add-Content $tempBat -Encoding Ascii
"set > `"$tempEnv`"" | Add-Content $tempBat -Encoding Ascii
cmd /c $tempBat

Get-Content $tempEnv | ForEach-Object {
    if ($_ -match "^([^=]+)=(.*)$") {
        $n = $Matches[1]
        $v = $Matches[2]
        [System.Environment]::SetEnvironmentVariable($n, $v, "Process")
    }
}

$clPath = (Get-Command cl.exe -ErrorAction SilentlyContinue)
if ($clPath) {
    Write-Host "cl.exe found: $($clPath.Source)" -ForegroundColor Green
} else {
    Write-Host "WARNING: cl.exe not found in PATH after vcvarsall" -ForegroundColor Red
}

# Android / cargo env
$env:CMAKE              = $CMAKE
$env:CMAKE_GENERATOR    = "Ninja"
$env:CMAKE_MAKE_PROGRAM = $NINJA
$env:ANDROID_NDK_HOME   = $NDK
$env:ANDROID_SDK_ROOT   = "C:\Users\dedek\AppData\Local\Android\Sdk"

# Short build dir to stay under Windows 260-char path limit.
# vulkan-shaders-gen intermediate paths hit 241+ chars with the default target/ location.
$env:CARGO_TARGET_DIR = "C:\lt"

# Ninja must be in PATH for the vulkan-shaders-gen sub-cmake
$env:PATH = "C:\Users\dedek\AppData\Local\Android\Sdk\cmake\3.22.1\bin;" + $env:PATH

# Clean stale cmake artifacts from the short target dir
Write-Host "Cleaning stale CMake artifacts..." -ForegroundColor Yellow
if (Test-Path "C:\lt") {
    Get-ChildItem "C:\lt" -Recurse -Filter "CMakeCache.txt"      -ErrorAction SilentlyContinue | Remove-Item -Force
    Get-ChildItem "C:\lt" -Recurse -Filter "CMakeFiles"           -ErrorAction SilentlyContinue | Where-Object { $_.PSIsContainer } | Remove-Item -Recurse -Force
    Get-ChildItem "C:\lt" -Recurse -Filter "*-stamp"              -ErrorAction SilentlyContinue | Where-Object { $_.PSIsContainer } | Remove-Item -Recurse -Force
    Get-ChildItem "C:\lt" -Recurse -Filter "host-toolchain.cmake" -ErrorAction SilentlyContinue | Remove-Item -Force
}

Push-Location "C:\Users\dedek\Desktop\Ledger\core"

Write-Host "Building arm64-v8a with Vulkan feature..." -ForegroundColor Cyan
cargo build --target aarch64-linux-android --release --features vulkan
if ($LASTEXITCODE -ne 0) {
    Pop-Location
    Write-Error "cargo build --features vulkan failed"
    exit 1
}

$src = "C:\lt\aarch64-linux-android\release\libuniffi_ledger.so"
$dst = "C:\Users\dedek\Desktop\Ledger\app\src\main\jniLibs\arm64-v8a\libuniffi_ledger.so"
Write-Host "Copying $src -> $dst"
Copy-Item -Force $src $dst

Pop-Location

$arch   = "aarch64-linux-android"
$libSrc = "$NDK\toolchains\llvm\prebuilt\windows-x86_64\sysroot\usr\lib\$arch\libc++_shared.so"
$libDst = "C:\Users\dedek\Desktop\Ledger\app\src\main\jniLibs\arm64-v8a\libc++_shared.so"
Write-Host "Copying libc++_shared.so -> $libDst"
Copy-Item -Force $libSrc $libDst

Write-Host "Vulkan build done. Run ./gradlew installDebug to install." -ForegroundColor Green
Write-Host "Check logcat for GPU_BACKEND=Vulkan or GPU_BACKEND=CPU in GemmaRepo." -ForegroundColor Yellow
