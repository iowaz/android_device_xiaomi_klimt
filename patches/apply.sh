#!/bin/bash
# usage: apply.sh <source tree root> [--check]; see apply.py.
set -euo pipefail
exec python3 "$(dirname "$(realpath "$0")")/apply.py" "${1:?usage: apply.sh <source tree root> [--check]}" "${@:2}"
