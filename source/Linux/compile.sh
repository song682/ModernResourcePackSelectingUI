#!/usr/bin/env bash
#
# Build libdragdrop.so for ModernResourcePackUI on Linux.
#
# Usage (from anywhere):
#   bash source/Linux/compile.sh
#
# Output goes to:
#   src/main/resources/natives/linux/libdragdrop.so   (shipped inside jar)
#   Linux/libdragdrop.so                              (repo-root mirror)
#
# Requirements on Ubuntu / Debian:
#   sudo apt install -y build-essential libx11-dev openjdk-8-jdk-headless
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SRC_FILE="${SCRIPT_DIR}/dragdrop.c"

# Locate JAVA_HOME - prefer JDK 8 to match the mod runtime.
if [[ -z "${JAVA_HOME:-}" ]]; then
    for candidate in \
        /usr/lib/jvm/java-8-openjdk-amd64 \
        /usr/lib/jvm/java-1.8.0-openjdk-amd64 \
        /usr/lib/jvm/default-java ; do
        if [[ -d "${candidate}/include" ]]; then
            export JAVA_HOME="${candidate}"
            break
        fi
    done
fi

if [[ -z "${JAVA_HOME:-}" || ! -d "${JAVA_HOME}/include" ]]; then
    echo "[compile.sh] ERROR: cannot find JDK headers. Install openjdk-8-jdk-headless or set JAVA_HOME." >&2
    exit 1
fi

echo "[compile.sh] JAVA_HOME = ${JAVA_HOME}"
echo "[compile.sh] source    = ${SRC_FILE}"

OUT_JAR_DIR="${PROJECT_ROOT}/src/main/resources/natives/linux"
OUT_ROOT_DIR="${PROJECT_ROOT}/Linux"
mkdir -p "${OUT_JAR_DIR}" "${OUT_ROOT_DIR}"

OUT_FILE="${OUT_JAR_DIR}/libdragdrop.so"

gcc -O2 -fPIC -shared -Wall -Wextra \
    -I"${JAVA_HOME}/include" \
    -I"${JAVA_HOME}/include/linux" \
    "${SRC_FILE}" \
    -lX11 \
    -o "${OUT_FILE}"

# Mirror to repo root so anyone poking at the project can find it.
cp -f "${OUT_FILE}" "${OUT_ROOT_DIR}/libdragdrop.so"

echo "[compile.sh] built  -> ${OUT_FILE}"
echo "[compile.sh] mirror -> ${OUT_ROOT_DIR}/libdragdrop.so"
ls -la "${OUT_FILE}"
file "${OUT_FILE}"
