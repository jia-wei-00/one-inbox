#!/usr/bin/env bash
# Prints the next release version (X.Y.Z).
# - HEAD already tagged vX.Y.Z: that version (a re-run reuses its release).
# - Otherwise: newest v*.*.* tag with the minor bumped (v1.3.0 -> 1.4.0).
# - No tags: 1.0.0.
set -euo pipefail

semver='^v[0-9]+\.[0-9]+\.[0-9]+$'

current=$(git tag --points-at HEAD --sort=-v:refname | grep -E "$semver" | head -n1 || true)
if [ -n "$current" ]; then
  echo "${current#v}"
  exit 0
fi

latest=$(git tag -l --sort=-v:refname | grep -E "$semver" | head -n1 || true)
if [ -z "$latest" ]; then
  echo "1.0.0"
  exit 0
fi

IFS=. read -r major minor _ <<< "${latest#v}"
echo "${major}.$(( minor + 1 )).0"
