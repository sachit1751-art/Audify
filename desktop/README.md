# Audify Desktop

Audify Desktop is a native Rust music client built with GPUI. It incorporates an existing open-source application foundation and is maintained here under the Audify product identity.

## License

Audify Desktop is distributed under **GNU General Public License v3.0 or later**. See [`COPYING`](COPYING). Required upstream copyright notices, author/contributor credits, and third-party license notices are retained. Distribution must comply with the GPL, including providing the complete corresponding source code and preserving applicable notices and license texts.

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

