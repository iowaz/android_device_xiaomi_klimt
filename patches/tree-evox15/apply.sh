#!/bin/bash
# Apply the patches in this directory to an Evolution X cnb tree: patches/tree/apply.sh <tree root>
# Each project's patches go on top of the revision listed in BASES; commits already present are skipped.
set -e
ROOT=$(realpath "${1:?usage: apply.sh <tree root>}"); HERE=$(dirname "$(realpath "$0")")
while read -r proj base; do
  for p in "$HERE/$proj"/*.patch; do
    subj=$(git mailinfo -k /dev/null /dev/null < "$p" | sed -n 's/^Subject: //p' | sed 's/^\[PATCH[^]]*\] //')
    if git -C "$ROOT/$proj" log -20 --format=%s | grep -qxF "$subj"; then echo "skip  $proj: $subj"; continue; fi
    git -C "$ROOT/$proj" am -q -3 "$p" && echo "apply $proj: $subj"
  done
done < "$HERE/BASES"
