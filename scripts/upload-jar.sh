#!/usr/bin/env bash
#
# Build the github-app .jar inside Docker, extract it, and upload it to the
# home directory of a remote host via scp.
#
# Usage: scripts/upload-jar.sh user@host.com
#
set -euo pipefail

# --- Arguments ---------------------------------------------------------------

if [[ $# -lt 1 ]]; then
    echo "Usage: $0 user@host.com" >&2
    exit 1
fi

remote="$1"

# --- Constants ---------------------------------------------------------------

# Repository root (this script lives in <root>/scripts).
repo_root="$(cd $(dirname "$0") && git rev-parse --show-toplevel)"

image_tag="github-app:upload"
# Unique-ish container name so concurrent runs don't collide.
container_name="github-app-extract-$$"
# Path to the jar inside the runtime image (see Dockerfile WORKDIR).
jar_in_image="/opt/github-app/github-app.jar"
# Filename the jar will have on the remote host.
remote_filename="github-app.jar"

# Local staging file.
jar_file="$(mktemp)"

# --- Cleanup -----------------------------------------------------------------

cleanup() {
    rm -f "$jar_file"
    docker rm --force "$container_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# --- Build -------------------------------------------------------------------

echo "==> Building image ${image_tag} from ${repo_root}"
docker build -t "$image_tag" "$repo_root"

# --- Extract -----------------------------------------------------------------

echo "==> Extracting ${jar_in_image} -> ${jar_file}"
# `docker create` materializes the container filesystem without running it.
docker create --name "$container_name" "$image_tag" >/dev/null
docker cp "${container_name}:${jar_in_image}" "$jar_file"

# --- Upload ------------------------------------------------------------------

echo "==> Uploading to ${remote}:~/${remote_filename}"
scp "$jar_file" "${remote}:${remote_filename}"

echo "==> Done. Uploaded as ~/${remote_filename} on ${remote}."
