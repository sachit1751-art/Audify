{
  config,
  lib,
  pkgs,
  ...
}:
let
  cfg = config.programs.audify;
  json = pkgs.formats.json { };
  generated = json.generate "audify-settings.json" ({ version = 1; } // cfg.settings);
  apply = pkgs.writeShellApplication {
    name = "audify-apply-settings";
    runtimeInputs = [ pkgs.jq ];
    text = ''
      generated=$1
      dest="''${XDG_CONFIG_HOME:-$HOME/.config}/audify/settings.json"
      mkdir -p "$(dirname "$dest")"
      if [[ -f "$dest" ]]; then
        jq --indent 2 -s '.[0] * .[1]' "$dest" "$generated" > "$dest.tmp"
        mv "$dest.tmp" "$dest"
      else
        cp "$generated" "$dest"
      fi
    '';
  };
  merge = "${lib.getExe apply} ${generated}";
  alsaPluginDir = pkgs.symlinkJoin {
    name = "audify-alsa-plugins";
    paths = [
      "${pkgs.pipewire}/lib/alsa-lib"
      "${pkgs.alsa-plugins}/lib/alsa-lib"
    ];
  };
  package = pkgs.symlinkJoin {
    name = "audify";
    paths = [ cfg.package ];
    nativeBuildInputs = [ pkgs.makeWrapper ];
    postBuild = ''
      rm -f $out/bin/audify
      makeWrapper ${lib.getExe cfg.package} $out/bin/audify \
        --set ALSA_PLUGIN_DIR ${alsaPluginDir} \
        ${lib.optionalString (cfg.settings != { }) "--run ${lib.escapeShellArg merge}"}
    '';
  };
in
{
  options.programs.audify = {
    enable = lib.mkEnableOption "Audify, a native, familiar music streaming client";

    package = lib.mkOption {
      type = lib.types.package;
    };

    settings = lib.mkOption {
      type = json.type;
      default = { };
      example = {
        provider = "youtube";
        gapless = true;
        appearance = {
          theme = "dark";
          icons = "solar";
        };
      };
      description = ''
        Configuration written to {file}`$XDG_CONFIG_HOME/audify/settings.json`.
        Same JSON keys as the app. Set keys are merged on switch and again each
        launch; omitted keys, including resume and window bounds, are kept.
      '';
    };
  };

  config = lib.mkIf cfg.enable {
    home.packages = [ package ];

    # write nix keys on switch; the wrapper merges them again on each launch
    home.activation.audifySettings = lib.mkIf (cfg.settings != { }) (
      lib.hm.dag.entryAfter [ "writeBoundary" ] ''
        $DRY_RUN_CMD ${merge}
      ''
    );
  };
}
