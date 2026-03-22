{ config, pkgs, ... }:
let
  cwd = builtins.getEnv "PWD";

  my = rec {
    jdk21 = pkgs.jdk21.override { enableJavaFX = false; };
  };
in
{
  # https://devenv.sh/guides/using-with-flakes/
  # Explicit root is needed for non-interactive flake evaluation (e.g. nix flake check),
  # but for direnv we must prefer the real runtime cwd (not the /nix/store flake source).
  devenv.root = if cwd != "" then cwd else builtins.toString ../.;

  # Note: devenv generates and injects a treefmt config from this Nix block.
  # Run with: treefmt (format) or treefmt --fail-on-change (check)
  treefmt = {
    enable = true;
    config = {
      projectRootFile = "flake.nix";
      programs = {
        deadnix.enable = true;
        nixfmt.enable = true;
        shfmt.enable = true;
        yamllint = {
          enable = true;
          settings = {
            extends = "default";
            rules = {
              document-start = "disable";
              truthy.check-keys = false;
              line-length.max = 100;
            };
          };
        };
        yamlfmt = {
          enable = true;
          settings.formatter = {
            include_document_start = true;
            pad_line_comments = 2;
          };
        };
        zizmor.enable = true;
      };
    };
  };

  # Extra checks/hooks that are not all treefmt formatters.
  git-hooks.hooks = {
    treefmt.enable = true;
    statix.enable = true;
    trufflehog.enable = true;
  };

  scripts.lint.exec = ''
    cd "${config.devenv.root}"

    treefmt --fail-on-change
  '';

  languages = {
    java = {
      enable = true;
      jdk.package = my.jdk21;
    };
  };

  packages = [
    my.jdk21

    pkgs.httpie
    pkgs.ripgrep
    pkgs.statix
    pkgs.treefmt
    pkgs.unzip
  ];
}
