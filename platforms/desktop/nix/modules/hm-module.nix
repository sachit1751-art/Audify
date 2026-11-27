self:
{ pkgs, lib, ... }:
{
  imports = [ ./hm/audify.nix ];
  programs.audify.package = lib.mkDefault self.packages.${pkgs.stdenv.hostPlatform.system}.default;
}
