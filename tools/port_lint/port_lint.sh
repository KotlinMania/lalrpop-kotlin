#!/bin/bash
set -euo pipefail
project="$(cd "$(dirname "$0")/../.." && pwd)"
exec "$project/tools/java-build-tool.sh" LalrpopPortLint "$@"
