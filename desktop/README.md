# Audify Desktop

Audify Desktop is a rebranded derivative of [Sonora](https://github.com/sonorahq/sonora), a native Rust music client built with GPUI. The Sonora implementation is retained as the application foundation; this fork changes the product identity and repository-facing metadata rather than replacing its functionality.

## License

This derivative is distributed under **GNU General Public License v3.0 or later**. See [`COPYING`](COPYING). Original Sonora copyright notices, author/contributor credits, and third-party license notices are retained. Distribution must comply with the GPL, including providing the complete corresponding source code and preserving applicable notices and license texts.

## Build on Windows

Use a Windows MSVC Rust toolchain, the Visual Studio C++ build tools, a Windows 10/11 SDK, and Inno Setup 6.

```powershell
cargo fmt --all -- --check
cargo check --locked --workspace
cargo test --locked --workspace
cargo build --release --locked --package audify
./scripts/windows/build-installer.ps1 -Version 1.8.10
```

The executable is written to `target/release/audify.exe`; the installer is written to `dist/Audify-Setup.exe`.

## Build

Install the Rust toolchain specified in `rust-toolchain.toml`, then run:

```powershell
cargo build --release -p audify
```

