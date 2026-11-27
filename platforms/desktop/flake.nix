{
  description = "A native, familiar music streaming client";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      rust-overlay,
      ...
    }:
    let
      inherit (nixpkgs) lib;

      forEachSystem =
        fn:
        lib.genAttrs lib.systems.flakeExposed (
          system:
          let
            pkgs = import nixpkgs {
              inherit system;
              overlays = [ (import rust-overlay) ];
            };
          in
          fn pkgs
        );

      release = {
        version = "0.47.0";
        assets = {
          x86_64-linux = {
            target = "x86_64-unknown-linux-gnu";
            hash = "sha256-kIbkTKG/vChrlqjHDo768Sk1QD001ufIttBxR864qlM=";
          };
          aarch64-linux = {
            target = "aarch64-unknown-linux-gnu";
            hash = "sha256-fj4JvzLkuSabpTMhvd49plSMAVqXC6025atwg9BqF8I=";
          };
          aarch64-darwin = {
            target = "macos";
            hash = "sha256-eqxote68x3W95ONQFDZs49drLPeRVc2/jr8juR+IGqA=";
          };
        };
      };

      # The nightly prerelease, in the same shape as `release` plus its tag. Only the commit the
      # nightly workflow tags `nightly` carries this file, so `github:sachit1751-art/Audify/nightly` is
      # the one flake ref that installs it.
      nightly = lib.optionalAttrs (builtins.pathExists ./nix/nightly.json) (
        lib.importJSON ./nix/nightly.json
      );
    in
    {
      packages = forEachSystem (
        pkgs:
        let
          runtimeLibraries =
            with pkgs;
            if pkgs.stdenv.hostPlatform.isLinux then
              [
                vulkan-loader
                wayland
                libxkbcommon
                libxcb
                libx11
                libxcursor
                libxi
                fontconfig
                freetype
                alsa-lib
                dbus
                sqlite
                webkitgtk_4_1
                glib-networking
              ]
            else
              [ ];

          inherit (pkgs.stdenv.hostPlatform) system;

          # WebKit plays a page's media through GStreamer and aborts its web process when no
          # audio sink element exists. The Nix webkitgtk closure carries only core and base, and
          # autoaudiosink lives in good, so the plugin path has to name all three.
          gstPluginPath = pkgs.lib.makeSearchPathOutput "lib" "lib/gstreamer-1.0" (
            with pkgs.gst_all_1;
            [
              gstreamer
              gst-plugins-base
              gst-plugins-good
            ]
          );

          alsaPluginDirectory = pkgs.symlinkJoin {
            name = "audify-alsa-plugins";
            paths = [
              "${pkgs.pipewire}/lib/alsa-lib"
              "${pkgs.alsa-plugins}/lib/alsa-lib"
            ];
          };

          # The source build uses the toolchain the devShell and CI pin, not whatever rustc
          # nixpkgs carries that day.
          rustPlatform =
            let
              toolchain =
                pkgs.rust-bin.stable.${(lib.importTOML ./rust-toolchain.toml).toolchain.channel}.minimal;
            in
            pkgs.makeRustPlatform {
              cargo = toolchain;
              rustc = toolchain;
            };

          # Points the Linux binary at its runtime libraries and environment. Both packages run
          # it, since webkitgtk and the GPU and audio stacks are opened with dlopen and never
          # show up as link-time dependencies.
          linuxFixup = lib.optionalString pkgs.stdenv.hostPlatform.isLinux ''
            patchelf \
              --set-interpreter "${pkgs.stdenv.cc.bintools.dynamicLinker}" \
              --add-rpath "${lib.makeLibraryPath (runtimeLibraries ++ [ pkgs.stdenv.cc.cc.lib ])}" \
              "$out/bin/audify"
            wrapProgram "$out/bin/audify" \
              --set ALSA_PLUGIN_DIR ${alsaPluginDirectory} \
              --prefix GIO_EXTRA_MODULES : ${pkgs.glib-networking}/lib/gio/modules \
              --prefix GST_PLUGIN_SYSTEM_PATH_1_0 : ${gstPluginPath}
          '';

          audify-src = rustPlatform.buildRustPackage (final: {
            pname = "audify";
            inherit ((lib.importTOML (final.src + /Cargo.toml)).workspace.package) version;

            src = ./.;
            cargoLock = {
              lockFile = final.src + /Cargo.lock;
              allowBuiltinFetchGit = true;
            };
            cargoBuildFlags = [
              "--package"
              "audify"
            ];
            doCheck = false;

            nativeBuildInputs =
              with pkgs;
              lib.flatten [
                cmake
                pkg-config
                (lib.optionals stdenv.hostPlatform.isLinux [
                  makeWrapper
                  mold
                ])
                (lib.optionals stdenv.hostPlatform.isDarwin [
                  (
                    let
                      pkgs' = import nixpkgs {
                        inherit (stdenv.hostPlatform) system;
                        config.allowUnfree = true;
                      };
                    in
                    runCommandLocal "metal-shader-compiler" { } ''
                      mkdir -p "$out/bin"
                      ln -s ${pkgs'.darwin.xcode}/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/metal "$out/bin/metal"
                      ln -s ${pkgs'.darwin.xcode}/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/metallib "$out/bin/metallib"
                    ''
                  )
                ])
              ];
            buildInputs =
              with pkgs;
              lib.flatten [
                sqlite
                (lib.optionals stdenv.hostPlatform.isLinux [
                  dbus
                  fontconfig
                  libxcb
                  libxkbcommon
                  libx11
                  pipewire
                  stdenv.cc.cc.lib
                  (alsa-lib-with-plugins.override {
                    plugins = [
                      alsa-plugins
                      pipewire
                    ];
                  })
                ])
                (lib.optionals stdenv.hostPlatform.isDarwin [
                  apple-sdk_15
                  (darwinMinVersionHook "10.15")
                ])
              ];

            installPhase = ''
              runHook preInstall

              install -Dm755 "target/${pkgs.stdenv.hostPlatform.rust.cargoShortTarget}/$cargoBuildType/audify" "$out/bin/audify"
              ${
                if pkgs.stdenv.hostPlatform.isDarwin then
                  ''
                    install -Dm755 "target/${pkgs.stdenv.hostPlatform.rust.cargoShortTarget}/$cargoBuildType/audify" "$out/Applications/Audify.app/Contents/MacOS/audify"
                    install -Dm444 "$src/assets/macos/audify.icns" \
                      "$out/Applications/Audify.app/Contents/Resources/audify.icns"

                    sed \
                      "s/@VERSION@/$version/g" \
                      "$src/assets/macos/Info.plist" \
                      > "$out/Applications/Audify.app/Contents/Info.plist"

                    LICENSE_DIR="$out/Applications/Audify.app/Contents/Resources"
                  ''
                else
                  ''
                    install -Dm444 "$src/assets/linux/audify.desktop" \
                      "$out/share/applications/audify.desktop"
                    install -Dm444 "$src/assets/linux/audify.svg" \
                      "$out/share/icons/hicolor/scalable/apps/audify.svg"
                    for icon in "$src"/assets/linux/icons/hicolor/*/apps/audify.png; do
                      size="$(basename "$(dirname "$(dirname "$icon")")")"
                      install -Dm444 "$icon" \
                        "$out/share/icons/hicolor/$size/apps/audify.png"
                    done

                    LICENSE_DIR="$out/share/licenses/audify"
                  ''
              }
              install -Dm444 "$src/COPYING" "$LICENSE_DIR/LICENSE"
              install -Dm444 "$src/THIRD-PARTY.md" "$LICENSE_DIR/THIRD-PARTY.md"
              install -Dm444 "$src/assets/fonts/LICENSE.txt" \
                "$LICENSE_DIR/audify/LICENSE.Inter"
              for licence in "$src/assets/icons"/*/LICENSE; do
                pack="$(basename "$(dirname "$licence")")"
                install -Dm444 "$licence" \
                  "$LICENSE_DIR/icons/LICENSE.$pack"
              done
              install -Dm444 "$src/assets/icons/LICENSE" \
                "$LICENSE_DIR/icons/LICENSE"

              runHook postInstall
            '';

            postFixup = linuxFixup;

            inherit meta;
          });

          meta = {
            description = "A native, familiar music streaming client";
            mainProgram = "audify";
            license = with lib.licenses; [
              gpl3Plus
              ofl
              isc
            ];
            platforms = lib.platforms.linux ++ lib.platforms.darwin;
          };

          # Installs the binaries a GitHub release publishes under `tag`. Every asset is named
          # `audify-<tag>-<target>`, and `assets` maps a Nix system to its target and hash.
          prebuilt =
            {
              pname,
              version,
              tag,
              assets,
            }:
            let
              asset = assets.${system};
            in
            pkgs.stdenv.mkDerivation {
              inherit pname version meta;

              src = pkgs.fetchurl {
                url = "https://github.com/sachit1751-art/Audify/releases/download/${tag}/audify-${tag}-${asset.target}${
                  if pkgs.stdenv.hostPlatform.isDarwin then ".dmg" else ""
                }";
                inherit (asset) hash;
              };

              dontUnpack = true;
              dontStrip = true;

              nativeBuildInputs =
                lib.optionals pkgs.stdenv.hostPlatform.isLinux [ pkgs.makeWrapper ]
                ++ lib.optionals pkgs.stdenv.hostPlatform.isDarwin [
                  pkgs.makeBinaryWrapper
                  pkgs.undmg
                ];

              installPhase =
                if pkgs.stdenv.hostPlatform.isLinux then
                  ''
                    runHook preInstall
                    install -Dm755 "$src" "$out/bin/audify"
                    install -Dm444 ${./assets/linux/audify.desktop} \
                      "$out/share/applications/audify.desktop"
                    install -Dm444 ${./assets/linux/audify.svg} \
                      "$out/share/icons/hicolor/scalable/apps/audify.svg"
                    for icon in ${./assets/linux/icons}/hicolor/*/apps/audify.png; do
                      size="$(basename "$(dirname "$(dirname "$icon")")")"
                      install -Dm444 "$icon" \
                        "$out/share/icons/hicolor/$size/apps/audify.png"
                    done
                    install -Dm444 ${./COPYING} "$out/share/licenses/audify/LICENSE"
                    install -Dm444 ${./THIRD-PARTY.md} "$out/share/licenses/audify/THIRD-PARTY.md"
                    install -Dm444 ${./assets/fonts/LICENSE.txt} \
                      "$out/share/licenses/audify/LICENSE.Inter"
                    for licence in ${./assets/icons}/*/LICENSE; do
                      pack="$(basename "$(dirname "$licence")")"
                      install -Dm444 "$licence" \
                        "$out/share/licenses/audify/icons/LICENSE.$pack"
                    done
                    install -Dm444 ${./assets/icons/LICENSE} \
                      "$out/share/licenses/audify/icons/LICENSE"
                    runHook postInstall
                  ''
                else
                  ''
                    runHook preInstall
                    mnt="$(mktemp -d)"
                    /usr/bin/hdiutil attach -readonly -nobrowse -mountpoint "$mnt" "$src"
                    mkdir -p "$out/Applications" "$out/bin"
                    cp -R "$mnt/Audify.app" "$out/Applications/Audify.app"
                    /usr/bin/hdiutil detach "$mnt"
                    makeBinaryWrapper \
                      "$out/Applications/Audify.app/Contents/MacOS/audify" \
                      "$out/bin/audify"
                    runHook postInstall
                  '';

              postFixup = linuxFixup;
            };

          audify-bin = prebuilt {
            pname = "audify-bin";
            inherit (release) version assets;
            tag = "v${release.version}";
          };

          audify-nightly = prebuilt {
            pname = "audify-nightly";
            inherit (nightly) version tag assets;
          };
        in
        # `default` and `audify` resolve to the release binary wherever one is published and to
        # the source build elsewhere. `audify-src` and `audify-bin` always mean one or the other.
        # At the `nightly` tag both resolve to the nightly binary instead.
        {
          inherit audify-src;
          audify = audify-src;
          default = audify-src;
        }
        // lib.optionalAttrs (builtins.hasAttr system release.assets) {
          inherit audify-bin;
          audify = audify-bin;
          default = audify-bin;
        }
        // lib.optionalAttrs (nightly ? assets && builtins.hasAttr system nightly.assets) {
          inherit audify-nightly;
          audify = audify-nightly;
          default = audify-nightly;
        }
      );

      devShells = forEachSystem (
        pkgs:
        let
          rustToolchain = pkgs.rust-bin.fromRustupToolchainFile ./rust-toolchain.toml;
          runtimeLibraries =
            with pkgs;
            if pkgs.stdenv.hostPlatform.isLinux then
              [
                vulkan-loader
                wayland
                libxkbcommon
                libxcb
                libx11
                libxcursor
                libxi
                fontconfig
                freetype
                alsa-lib
                dbus
                sqlite
                openssl
                webkitgtk_4_1
                glib-networking
              ]
            else
              [ ];
          # Apple's own xcrun, so `xcrun metal` can reach the Metal toolchain that
          # Xcode 26 mounts outside DEVELOPER_DIR. The xcbuild shim the Apple SDK
          # drags onto PATH cannot, and neither can any xcrun pointed at the Nix SDK.
          xcodeXcrun = pkgs.runCommandLocal "xcode-xcrun" { } ''
            mkdir -p $out/bin
            ln -s /usr/bin/xcrun $out/bin/xcrun
          '';
        in
        {
          default = pkgs.mkShell {
            nativeBuildInputs =
              (with pkgs; [
                pkg-config
                cmake
                rustToolchain
                sccache
              ])
              ++ lib.optionals pkgs.stdenv.hostPlatform.isLinux [ pkgs.mold ];

            buildInputs = runtimeLibraries;

            LD_LIBRARY_PATH = lib.makeLibraryPath runtimeLibraries;

            ALSA_PLUGIN_DIR =
              if pkgs.stdenv.hostPlatform.isLinux then
                "${pkgs.symlinkJoin {
                  name = "alsa-plugins-combined";
                  paths = [
                    "${pkgs.alsa-plugins}/lib/alsa-lib"
                    "${pkgs.pipewire}/lib/alsa-lib"
                  ];
                }}"
              else
                "";

            shellHook =
              # WebKit composites through EGL, which has to find the same drivers.
              pkgs.lib.optionalString pkgs.stdenv.hostPlatform.isLinux ''
                if [ ! -d /run/opengl-driver ]; then
                  export VK_DRIVER_FILES="${pkgs.mesa}/share/vulkan/icd.d"
                  export VK_IMPLICIT_LAYER_PATH="${pkgs.mesa}/share/vulkan/implicit_layer.d"
                  export __EGL_VENDOR_LIBRARY_DIRS="${pkgs.mesa}/share/glvnd/egl_vendor.d"
                  export LIBGL_DRIVERS_PATH="${pkgs.mesa}/lib/dri"
                  export GBM_BACKENDS_PATH="${pkgs.mesa}/lib/gbm"
                fi
                export GIO_EXTRA_MODULES="${pkgs.glib-networking}/lib/gio/modules''${GIO_EXTRA_MODULES:+:$GIO_EXTRA_MODULES}"
                export GST_PLUGIN_SYSTEM_PATH_1_0="${
                  pkgs.lib.makeSearchPathOutput "lib" "lib/gstreamer-1.0" (
                    with pkgs.gst_all_1;
                    [
                      gstreamer
                      gst-plugins-base
                      gst-plugins-good
                    ]
                  )
                }''${GST_PLUGIN_SYSTEM_PATH_1_0:+:$GST_PLUGIN_SYSTEM_PATH_1_0}"
              ''
              # gpui_apple compiles its shaders with `xcrun -sdk macosx metal` at build
              # time. The Nix Apple SDK has no Metal toolchain, so hand xcrun back to the
              # installed Xcode. Clang follows DEVELOPER_DIR too and then reads Xcode's C
              # headers beside the Nix libc++, which breaks any C++ a build script compiles,
              # so C++ compiles are pinned to the Nix SDK through CXXFLAGS. Only compiles: the
              # link still has to see Xcode's SDK, which is where libsqlite3 comes from.
              + lib.optionalString pkgs.stdenv.hostPlatform.isDarwin ''
                # xcode-select echoes DEVELOPER_DIR back when it is set, so ask with it unset.
                if xcode="$(env -u DEVELOPER_DIR /usr/bin/xcode-select -p 2>/dev/null)"; then
                  export DEVELOPER_DIR="$xcode"
                  export PATH="${xcodeXcrun}/bin:$PATH"
                  export CXXFLAGS="''${CXXFLAGS:-} -isysroot $SDKROOT"
                fi
              '';
          };
        }
      );

      overlays.default = final: _prev: {
        audify = self.packages.${final.stdenv.hostPlatform.system}.default;
      };

      homeManagerModules = {
        default = import ./nix/modules/hm-module.nix self;
        audify = import ./nix/modules/hm-module.nix self;
      };

      homeModules = self.homeManagerModules;
    };
}
