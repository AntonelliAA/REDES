# Servidor HTTP/1.1 sobre Sockets TCP em Java

Servidor de arquivos estáticos construído com `ServerSocket` e `Socket`, sem biblioteca HTTP de servidor. Implementa os requisitos de GET, HEAD, concorrência, proteção do diretório raiz e conexões persistentes do [enunciado](Enunciado.md).

## Compilação e execução

Requer JDK 17 ou superior. Os scripts usam Bash; os testes em rede também usam `curl` e Wireshark. Confirme que o JDK está disponível na VDI, sem instalar um runtime adicional.

Execute os comandos na raiz do projeto:

```bash
bash scripts/compile.sh
bash scripts/test.sh
bash scripts/run.sh --port 8080 --root ./www
```

A compilação gera `out/main` e `out/test`. Os scripts de teste e execução também compilam o projeto. O servidor escuta em `0.0.0.0` e deve ser acessado pelo IP da máquina na rede.

| Argumento | Obrigatório | Padrão | Descrição |
| :--- | :---: | :--- | :--- |
| `--port <porta>` | Sim | — | Porta entre 1025 e 65535. |
| `--root <diretório>` | Sim | — | Diretório existente que contém os arquivos servidos. |
| `--idle-timeout <ms>` | Não | `5000` | Timeout de leitura ociosa da conexão, em milissegundos. |
| `--workers <quantidade>` | Não | `max(4, CPUs)` | Quantidade de threads de atendimento. Use pelo menos 2 no teste de concorrência. |

Exemplo:

```bash
bash scripts/run.sh --port 8080 --root ./www --idle-timeout 5000 --workers 8
```

## Arquitetura

- `ServerMain` aceita conexões e as entrega a um pool fixo de threads. Cada conexão ocupa uma thread enquanto é atendida; conexões adicionais aguardam uma thread livre. Um cliente lento não bloqueia os demais enquanto há threads disponíveis.
- `HttpRequestReader` acumula bytes até `\r\n\r\n` e preserva o excedente para a próxima requisição.
- `StaticFileService` decodifica o caminho, verifica os limites da raiz e resolve caminhos reais para impedir acesso por links simbólicos externos.
- `HttpResponseWriter` escreve status, `Date` em GMT, `Server`, tipo e tamanho do conteúdo. HEAD recebe os cabeçalhos correspondentes ao GET, sem corpo.
- `HttpConnectionHandler` mantém a conexão por padrão e a encerra quando solicitado com `Connection: close`, por timeout ou quando necessário para rejeitar uma requisição inválida.

O servidor não processa corpos de requisição. Se houver `Content-Length` positivo ou `Transfer-Encoding`, responde com `Connection: close` para não interpretar o corpo como outra requisição. `Content-Length: 0` permite persistência; métodos não suportados recebem 405.

## Verificação dos métodos e códigos de status

Na máquina cliente, substitua o IP abaixo pelo IP real do servidor. Os comandos não constituem evidências até que suas respostas sejam registradas.

```bash
BASE='http://192.168.1.100:8080'

# 200: GET e HEAD; Content-Length deve corresponder ao tamanho do arquivo.
curl --http1.1 -i "$BASE/index.html"
curl --http1.1 -I "$BASE/index.html"

# 400: o espaço inserido no alvo torna a linha de requisição inválida.
curl --http1.1 -i --request-target '/ alvo-invalido' "$BASE/"

# 403: três travessias distintas, incluindo percent-encoding.
# --path-as-is impede o curl de remover ../ antes de enviar o caminho.
curl --http1.1 --path-as-is -i "$BASE/../../etc/passwd"
curl --http1.1 --path-as-is -i "$BASE/%2e%2e/%2e%2e/outside.txt"
curl --http1.1 --path-as-is -i "$BASE/safe/%2e%2e/%2e%2e/outside.txt"

# 404: arquivo inexistente.
curl --http1.1 -i "$BASE/arquivo-inexistente.html"

# 405: método não suportado; conferir Allow: GET, HEAD.
curl --http1.1 -i -X POST "$BASE/index.html"

# Conferir Connection: close na resposta e o encerramento na captura.
curl --http1.1 -i -H 'Connection: close' "$BASE/index.html"
```

## Testes na rede e entrega

Antes de medir, identifique os IPs (`ipconfig`, `ip addr` ou `ifconfig`), confirme o alcance com `ping` e teste a captura na interface Wi-Fi/Ethernet. As medições exigidas devem ocorrer **entre máquinas distintas**. A suíte local valida o código, mas não substitui esses testes.

Abra `http://<IP_DO_SERVIDOR>:8080/` no navegador de outra máquina e confira o carregamento do HTML, de `style.css` e de `pixel.png`. O navegador pode usar mais de uma conexão para esses recursos. Faça também o teste de duas máquinas clientes atendidas ao mesmo tempo.

No Wireshark, use `tcp port 8080` como **filtro de captura** ou `tcp.port == 8080` como **filtro de exibição**. Siga [capturas/README.md](capturas/README.md) para executar:

```bash
bash scripts/measure-c1.sh "http://<IP_DO_SERVIDOR>:8080/index.html"
bash scripts/measure-c2.sh "http://<IP_DO_SERVIDOR>:8080/index.html"
```

Os scripts realizam 10 GETs sequenciais, exigem respostas 200 e conferem as conexões abertas pelo curl: 10 em C1 e 1 em C2. Não dependem de Python. Pacotes, bytes, handshakes completos e tempo total devem ser extraídos das capturas.

Preencha [capturas/medicoes.md](capturas/medicoes.md) com os valores reais e finalize [relatorio/roteiro.md](relatorio/roteiro.md). O roteiro ainda precisa das evidências de rede e deve ser exportado para **um único PDF**. Inclua no `.zip` ou `.tar` os fontes, README, scripts, `www/`, capturas `.pcapng` e o relatório em PDF; exclua `out/`, `.git/`, temporários e binários de compilação.
