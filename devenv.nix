{ pkgs, ... }:

{
  # https://devenv.sh/basics/
  env.GREET = "devenv";

  # https://devenv.sh/packages/
  packages = [
    pkgs.git
    pkgs.nixpkgs-fmt
    pkgs.nodePackages.eslint
  ];

  # https://devenv.sh/scripts/
  scripts.rush.exec = "node ./common/scripts/install-run-rush.js $@";

  # enterShell = ''
  # '';

  # https://devenv.sh/languages/
  # languages.nix.enable = true;
  languages.javascript = {
    enable = true;
    package = pkgs.nodejs_18;
    pnpm.enable = true;
  };

  # https://devenv.sh/pre-commit-hooks/
  # pre-commit.hooks.shellcheck.enable = true;

  # https://devenv.sh/processes/
  # processes.ping.exec = "ping example.com";

  # See full reference at https://devenv.sh/reference/options/
}
