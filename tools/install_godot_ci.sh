#!/usr/bin/env bash
set -euo pipefail
version=4.4.1-stable
base="https://github.com/godotengine/godot-builds/releases/download/${version}"
if [[ "${1:-}" == templates ]]; then
  curl --fail --location --retry 3 "${base}/Godot_v${version}_export_templates.tpz" -o /tmp/godot-templates.tpz
  mkdir -p "$HOME/.local/share/godot/export_templates/4.4.1.stable"
  unzip -q /tmp/godot-templates.tpz -d /tmp/godot-export
  mv /tmp/godot-export/templates/* "$HOME/.local/share/godot/export_templates/4.4.1.stable/"
else
  curl --fail --location --retry 3 "${base}/Godot_v${version}_linux.x86_64.zip" -o /tmp/godot.zip
  unzip -q /tmp/godot.zip -d /tmp/codegaze-godot
  sudo install /tmp/codegaze-godot/Godot_v${version}_linux.x86_64 /usr/local/bin/godot
fi
