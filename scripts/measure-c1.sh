#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 1 ]; then
    echo "Uso: $0 <URL>" >&2
    echo "Exemplo: $0 http://192.168.1.100:8080/index.html" >&2
    exit 1
fi

url="$1"
args=()
for ((i = 1; i <= 10; i++)); do
    args+=(--output /dev/null --url "$url")
done

echo "C1: 10 requisições sequenciais com Connection: close para $url"
# Um único processo, mas o servidor fecha a conexão após cada resposta.
resultados=$(curl --disable --http1.1 --noproxy '*' --globoff \
    --silent --show-error --fail --fail-early \
    --connect-timeout 5 --max-time 30 --header 'Connection: close' \
    --write-out '%{http_code} %{num_connects}\n' "${args[@]}")
# curl no Windows pode terminar cada linha com CRLF.
resultados=${resultados//$'\r'/}

requisicoes=0
while read -r status conexoes; do
    if [ "$status" != 200 ] || [ "$conexoes" != 1 ]; then
        echo "C1 inválido: esperado HTTP 200 e uma conexão nova por requisição." >&2
        exit 1
    fi
    requisicoes=$((requisicoes + 1))
done <<< "$resultados"

if [ "$requisicoes" -ne 10 ]; then
    echo "C1 inválido: esperado 10 respostas, recebido $requisicoes." >&2
    exit 1
fi

echo "C1 concluído: 10 respostas HTTP 200 em 10 conexões TCP."
echo "Extraia pacotes, bytes e tempo total da captura Wireshark."
