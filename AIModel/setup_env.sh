#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

python3 -m venv env
./env/bin/python -m pip install --upgrade pip setuptools wheel
./env/bin/python -m pip install -r requirements.txt

echo "AIModel Python environment is ready: $(pwd)/env"
