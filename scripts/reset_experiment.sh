#!/bin/bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

DATA_DIR="${DATA_DIR:-data}"

if [ -z "$DATA_DIR" ]; then
    echo "Refusing to reset: DATA_DIR is empty"
    exit 1
fi

if [[ "$DATA_DIR" != /* ]]; then
    DATA_DIR="$REPO_ROOT/$DATA_DIR"
fi

if [ "$DATA_DIR" = "/" ] || [ "$DATA_DIR" = "$HOME" ]; then
    echo "Refusing to reset unsafe DATA_DIR: $DATA_DIR"
    exit 1
fi

if [ ! -d "$DATA_DIR" ]; then
    echo "Refusing to reset: DATA_DIR does not exist: $DATA_DIR"
    exit 1
fi

echo "Resetting Stage 2 experiment"
echo "DATA_DIR=$DATA_DIR"

rm -rf "$DATA_DIR/datalake"
rm -rf "$DATA_DIR/datamart"
rm -rf "$DATA_DIR/control"

mkdir -p "$DATA_DIR/datalake"
mkdir -p "$DATA_DIR/datamart"
mkdir -p "$DATA_DIR/control"

echo
echo "Experiment state reset:"
echo "  datalake -> empty"
echo "  datamart -> empty"
echo "  control  -> empty"
echo
echo "RESET COMPLETE"