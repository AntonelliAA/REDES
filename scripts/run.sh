#!/usr/bin/env bash
set -euo pipefail

bash scripts/compile.sh
exec java -cp out/main br.edu.redes.http.ServerMain "$@"

