{
  description = "Omni project with openspec and Clojure development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
    devenv.url = "github:cachix/devenv";

    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    inputs@{ self, flake-parts, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      imports = [ inputs.treefmt-nix.flakeModule ];

      systems = [
        "x86_64-linux"
        "aarch64-linux"
        "aarch64-darwin"
      ];

      perSystem =
        { config, pkgs, ... }:
        {
          checks = {
            formatting = config.treefmt.build.check self;
          };

          devShells.default = inputs.devenv.lib.mkShell {
            inherit inputs pkgs;
            modules = [
              (import ./nix/devshell.nix)
            ];
          };

          formatter = config.treefmt.build.wrapper;
        };
    };
}
