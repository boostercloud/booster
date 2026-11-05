#!/bin/sh
# Optional Linux x86_64 toolchain bootstrap. Other platforms may use any JDK 27 vendor.
set -eu
base=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
[ "$(uname -s)" = Linux ] && [ "$(uname -m)" = x86_64 ] || {
  echo 'Install a JDK 27 for your platform and set JAVA_HOME.' >&2; exit 1;
}
tools_dir=${BOOSTER_TOOLS_DIR:-"$base/.tools"}
version=27.0.1.0.3
if [ ! -x "$tools_dir/jdk-$version/bin/java" ]; then
  mkdir -p "$tools_dir"
  download=$(mktemp -d "$tools_dir/jdk-download.XXXXXX")
  trap 'rm -rf "$download"' EXIT HUP INT TERM
  curl --fail --location --silent --show-error --retry 3 \
    "https://download.oracle.com/java/27/archive/jdk-${version}_linux-x64_bin.tar.gz" \
    -o "$download/jdk.tar.gz"
  actual=$(sha256sum "$download/jdk.tar.gz" | cut -d ' ' -f 1)
  [ "$actual" = d41bdc29a13920e90ee0f8e97a5bb9cb854ac06e0aa648a7fda1620db6c4e1ee ] || { echo 'JDK checksum verification failed' >&2; exit 1; }
  tar -xzf "$download/jdk.tar.gz" -C "$download"
  mv "$download/jdk-$version" "$tools_dir/jdk-$version"
  rm -rf "$download"
  trap - EXIT HUP INT TERM
fi
"$tools_dir/jdk-$version/bin/java" -version
