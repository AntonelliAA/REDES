# Servidor HTTP/1.1 sobre Sockets TCP

Trabalho 1 de Laboratório de Redes de Computadores. É um servidor de arquivos estáticos escrito em Java, usando só `ServerSocket` e `Socket`. Toda a leitura das requisições e a montagem das respostas HTTP foram feitas por nós, sem biblioteca HTTP.

O servidor responde a `GET` e `HEAD`, devolve `200`, `400`, `403`, `404` e `405`, bloqueia acesso fora do diretório raiz, atende várias conexões ao mesmo tempo e mantém a conexão aberta entre requisições (HTTP/1.1 persistente), com timeout de ociosidade.

## O que tem aqui

| Pasta | Conteúdo |
| :--- | :--- |
| `src/` | Código do servidor |
| `test/` | Testes automatizados (Java puro, sem bibliotecas) |
| `scripts/` | Scripts para compilar, testar, executar e medir C1/C2 |
| `www/` | Página usada no teste de interoperabilidade (HTML + CSS + imagem) |
| `capturas/` | Capturas do Wireshark: `c1.pcapng`, `c2.pcapng` e `concorrencia.pcapng` |
| `relatorio/` | Relatório em PDF (e o fonte LaTeX) |

## Como executar

Precisa do **JDK 17 ou mais novo**. Não há outras dependências.

**1. Abra um terminal na pasta do projeto.**

**2. Compile e suba o servidor:**

```bash
bash scripts/run.sh --port 8080 --root ./www
```

O script compila tudo em `out/` e inicia o servidor. Ele deve mostrar:

```text
Servidor HTTP/1.1 escutando em 0.0.0.0:8080
```

**3. Descubra o IP da máquina** (`ipconfig` no Windows, `ipconfig getifaddr en0` no macOS, `ip addr` no Linux).

**4. Em outra máquina da rede, abra no navegador** `http://<IP_DO_SERVIDOR>:8080/`. A página deve carregar com o estilo e a imagem.

Para parar o servidor, use `Ctrl+C`.

### Sem Bash (Windows, PowerShell ou CMD)

```bat
javac --release 17 -encoding UTF-8 -d out\main src\br\edu\redes\http\*.java
java -cp out\main br.edu.redes.http.ServerMain --port 8080 --root .\www
```

### Rodar os testes

```bash
bash scripts/test.sh
```

Cada classe de teste imprime `OK` quando passa.

## Argumentos

| Argumento | Obrigatório | Padrão | Para que serve |
| :--- | :---: | :---: | :--- |
| `--port <número>` | Sim | — | Porta do servidor, de 1025 a 65535 |
| `--root <pasta>` | Sim | — | Pasta com os arquivos que serão servidos |
| `--idle-timeout <ms>` | Não | `5000` | Tempo em milissegundos que uma conexão pode ficar parada antes de ser fechada |
| `--workers <número>` | Não | maior entre 4 e o nº de CPUs | Quantas conexões são atendidas ao mesmo tempo (mínimo 2) |

Exemplo com todos os argumentos:

```bash
bash scripts/run.sh --port 8080 --root ./www --idle-timeout 5000 --workers 32
```

## Arquitetura

O caminho de uma requisição passa por cinco classes, nesta ordem:

```text
cliente ──TCP──> ServerMain ──> HttpConnectionHandler (uma thread do pool)
                                   │
                                   ├─> HttpRequestReader   lê e interpreta a requisição
                                   ├─> StaticFileService   encontra o arquivo com segurança
                                   └─> HttpResponseWriter  envia a resposta
                                   │
                                   └─ repete na mesma conexão até close, timeout ou o cliente sair
```

- **`ServerMain`** abre o socket em `0.0.0.0` (todas as interfaces, para aceitar outras máquinas), espera conexões com `accept()` e entrega cada uma a um pool de threads.
- **`HttpConnectionHandler`** cuida de uma conexão inteira. Atende uma requisição atrás da outra e só fecha quando o cliente pede `Connection: close`, quando passa o timeout sem dados ou quando a requisição é inválida.
- **`HttpRequestReader`** junta os bytes que chegam do socket até encontrar a linha em branco (`\r\n\r\n`) que termina os cabeçalhos. Como o TCP é um fluxo contínuo, uma leitura pode trazer meia requisição ou o começo da próxima. O que sobra fica guardado para a requisição seguinte. Requisição malformada gera `400`.
- **`StaticFileService`** decodifica o percent-encoding do caminho (`%20`, `%2e`…), resolve os `..` e confere se o resultado continua dentro da pasta raiz. Se não continuar, responde `403`. Também define o `Content-Type` pela extensão do arquivo.
- **`HttpResponseWriter`** escreve a linha de status e os cabeçalhos `Date` (GMT), `Server`, `Content-Length` e `Content-Type`. No `GET` envia o arquivo em blocos de 16 KiB; no `HEAD` envia os mesmos cabeçalhos, sem o corpo.
- **`ServerConfig`** lê os argumentos da linha de comando. **`HttpRequest`**, **`HttpResponse`**, **`FileResult`** e **`BadRequestException`** só carregam dados entre as classes acima.

### Por que um pool de threads

Cada conexão é atendida por uma thread própria, tirada de um pool de tamanho fixo. O código de cada conexão fica simples: lê, processa, responde e repete. Uma conexão lenta prende só a sua thread, e as outras continuam sendo atendidas. O pool também impede que uma rajada de conexões crie threads sem limite: as que chegam com todas as threads ocupadas esperam na fila.

Limitações que conhecemos: o timeout vale só para leitura, e a fila do pool não tem tamanho máximo.

## Testando com curl

Troque o IP pelo do servidor. A opção `--path-as-is` impede o curl de remover os `../` antes de enviar.

```bash
BASE='http://192.168.1.100:8080'

curl -i "$BASE/index.html"                                  # 200
curl -I "$BASE/index.html"                                  # 200, só cabeçalhos (HEAD)
curl -i --request-target '/ invalido' "$BASE/"              # 400
curl -i --path-as-is "$BASE/../../etc/passwd"               # 403
curl -i --path-as-is "$BASE/%2e%2e/%2e%2e/etc/passwd"       # 403
curl -i "$BASE/nao-existe.html"                             # 404
curl -i -X POST "$BASE/index.html"                          # 405, com Allow: GET, HEAD
```

As medições C1 (uma conexão por requisição) e C2 (conexão persistente) foram feitas com `scripts/measure-c1.sh` e `scripts/measure-c2.sh`, que recebem a URL do servidor. Os resultados e a análise estão no relatório.
