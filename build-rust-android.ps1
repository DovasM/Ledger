# Builds the Rust core for Android and copies .so files to jniLibs.
# Run from the project root: .\build-rust-android.ps1
# First build takes ~20-30 min (compiles llama.cpp C++ for Android).

$ErrorActionPreference = "Stop"

$NDK    = "C:\Users\dedek\AppData\Local\Android\Sdk\ndk\30.0.14904198"
$CMAKE  = "C:\Users\dedek\AppData\Local\Android\Sdk\cmake\3.22.1\bin\cmake.exe"
$NINJA  = "C:\Users\dedek\AppData\Local\Android\Sdk\cmake\3.22.1\bin\ninja.exe"

$env:CMAKE             = $CMAKE
$env:CMAKE_GENERATOR   = "Ninja"
$env:CMAKE_MAKE_PROGRAM = $NINJA
$env:ANDROID_NDK_HOME  = $NDK

$targets = @(
    @{ Triple = "aarch64-linux-android"; AbiDir = "arm64-v8a" },
    @{ Triple = "x86_64-linux-android";  AbiDir = "x86_64" }
)

Push-Location "$PSScriptRoot\core"

# Remove stale CMake caches that block generator change (VS → Ninja)
Write-Host "Cleaning stale CMake caches..." -ForegroundColor Yellow
Get-ChildItem "target" -Recurse -Filter "CMakeCache.txt" -ErrorAction SilentlyContinue | Remove-Item -Force
Get-ChildItem "target" -Recurse -Filter "CMakeFiles" -ErrorAction SilentlyContinue | Where-Object { $_.PSIsContainer } | Remove-Item -Recurse -Force

foreach ($t in $targets) {
    Write-Host "`n==> Building $($t.Triple) ..." -ForegroundColor Cyan
    cargo build --target $t.Triple --release
    if ($LASTEXITCODE -ne 0) { throw "cargo build failed for $($t.Triple)" }

    $src = "target\$($t.Triple)\release\libuniffi_ledger.so"
    $dst = "..\app\src\main\jniLibs\$($t.AbiDir)\libuniffi_ledger.so"
    Write-Host "    Copying $src -> $dst"
    Copy-Item -Force $src $dst
}

Pop-Location

# Copy libc++_shared.so from NDK — required at runtime because libuniffi_ledger.so links it dynamically
$abiArchMap = @{
    "arm64-v8a" = "aarch64-linux-android"
    "x86_64"    = "x86_64-linux-android"
}
foreach ($t in $targets) {
    $arch    = $abiArchMap[$t.AbiDir]
    $libSrc  = "$NDK\toolchains\llvm\prebuilt\windows-x86_64\sysroot\usr\lib\$arch\libc++_shared.so"
    $libDst  = "app\src\main\jniLibs\$($t.AbiDir)\libc++_shared.so"
    Write-Host "    Copying libc++_shared.so -> $libDst"
    Copy-Item -Force $libSrc $libDst
}

Write-Host "`nDone. Run ./gradlew assembleDebug to build the APK." -ForegroundColor Green
