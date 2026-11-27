# Audify Desktop

Audify Desktop is a native Rust music client built with GPUI. It incorporates an existing open-source application foundation and is maintained here under the Audify product identity.

## Download

Windows releases provide separate native installers for **32-bit x86**, **64-bit x64**, and **ARM64** PCs. Download the matching `Audify-Setup-x86.exe`, `Audify-Setup.exe`, or `Audify-Setup-arm64.exe` from the [latest GitHub release](https://github.com/sachit1751-art/Audify/releases/latest). The installer is unsigned; Windows may show an unrecognized-publisher warning.

## License

Audify Desktop is distributed under **GNU General Public License v3.0 or later**. See [`COPYING`](COPYING). Required upstream copyright notices, author/contributor credits, and third-party license notices are retained. Distribution must comply with the GPL, including providing the complete corresponding source code and preserving applicable notices and license texts.

## Build on Windows

Use a Windows MSVC Rust toolchain, the Visual Studio C++ build tools, a Windows 10/11 SDK, and Inno Setup 6.

```powershell
cargo fmt --all -- --check
cargo check --locked --workspace
cargo test --locked --workspace
cargo build --release --locked --package audify
./scripts/windows/build-installer.ps1 -Version 1.9.1

# 32-bit x86 Windows
rustup target add i686-pc-windows-msvc
cargo build --release --locked --package audify --target i686-pc-windows-msvc
./scripts/windows/build-installer.ps1 -Version 1.9.1 -Architecture x86 -Executable target/i686-pc-windows-msvc/release/audify.exe
```

The x64 executable is written to `target/release/audify.exe`; the installer is written to `dist/Audify-Setup.exe`. The x86 build writes to `target/i686-pc-windows-msvc/release/audify.exe` and `dist/Audify-Setup-x86.exe`. ARM64 uses `target/aarch64-pc-windows-msvc/release/audify.exe` and `dist/Audify-Setup-arm64.exe`. The 32-bit x86 build cannot use Google's Widevine CDM because Google does not publish a 32-bit Windows module.

## Build

Install the Rust toolchain specified in `rust-toolchain.toml`, then run:

```powershell
cargo build --release -p audify
```
