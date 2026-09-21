# Servidor HTTP/1.1 sobre sockets TCP em Java — Plano de Implementação

> **Para agentes executores:** SUB-SKILL OBRIGATÓRIA: use `superpowers:subagent-driven-development` (recomendado) ou `superpowers:executing-plans` para executar este plano task por task. Marque cada item `- [ ]` ao concluir. Leia o enunciado e esta seção global antes de editar qualquer arquivo.

**Objetivo:** Entregar um servidor HTTP/1.1 em Java, implementado diretamente sobre sockets TCP, com arquivos estáticos, GET/HEAD, erros obrigatórios, proteção contra travessia, concorrência, conexões persistentes e evidências reproduzíveis para o relatório.

**Arquitetura:** Um `ServerSocket` ligado a `0.0.0.0` aceita conexões e delega cada socket a um pool fixo de threads. Cada conexão mantém um único buffer incremental, extrai uma requisição por vez sem perder bytes excedentes, produz uma resposta e permanece aberta até `Connection: close`, timeout ou EOF. Resolução de arquivos e serialização HTTP ficam separadas do laço de conexão para permitir testes determinísticos sem rede.

**Tech Stack:** Java 17 ou versão disponível na VDI; somente JDK (`java.net`, `java.io`, `java.nio`, `java.time`, `java.util.concurrent`); shell; `curl`; Wireshark; sem Maven/Gradle e sem biblioteca HTTP de servidor.

**Spec:** `/Users/anthony/.codex/attachments/dd8f9ae7-8217-4fa2-95b2-ee637219a7df/pasted-text.txt`

## Restrições globais

- Usar diretamente `ServerSocket`, `Socket`, `InputStream` e `OutputStream`; nenhuma API que implemente servidor HTTP.
- Aceitar no mínimo `--port <porta-acima-de-1024>` e `--root <diretório>`.
- Escutar em `0.0.0.0`, nunca somente em loopback.
- Interpretar bytes até `\r\n\r\n`, preservar bytes posteriores e limitar cabeçalhos a 64 KiB.
- Aceitar somente `GET` e `HEAD`; outros métodos retornam `405` e `Allow: GET, HEAD`.
- Toda resposta inclui `HTTP/1.1`, `Content-Length`, `Content-Type`, `Date` em IMF-fixdate/GMT e `Server`.
- Implementar `200`, `400`, `403`, `404` e `405` nos casos definidos no enunciado.
- Nunca servir caminho fora da raiz após percent-decoding e normalização.
- HTTP/1.1 persistente por padrão; `Connection: close` fecha após resposta; timeout ocioso padrão de 5 s.
- Uma conexão lenta não pode bloquear as demais.
- Não registrar corpos binários no console; logs devem identificar horário, cliente, método, alvo, status e estado da conexão.
- Cada agente altera apenas os arquivos da sua task. Se precisar mudar uma interface publicada por task anterior, deve parar e devolver a incompatibilidade ao agente coordenador.
- Cada task termina com sua verificação e um commit pequeno. Não incluir `.class`, capturas temporárias ou arquivos de build no commit.

## Estrutura de arquivos final

```text
.
├── README.md
├── .gitignore
├── src/br/edu/redes/http/
│   ├── ServerMain.java            # CLI, bind, accept e encerramento
│   ├── ServerConfig.java          # configuração validada
│   ├── HttpConnectionHandler.java # ciclo de vida de uma conexão
│   ├── HttpRequestReader.java     # buffer incremental do fluxo TCP
│   ├── HttpRequest.java           # requisição já validada
│   ├── BadRequestException.java   # sinalização de requisição inválida
│   ├── StaticFileService.java     # caminho seguro e leitura do recurso
│   ├── FileResult.java            # resultado da resolução de arquivo
│   ├── HttpResponse.java          # representação da resposta
│   └── HttpResponseWriter.java    # serialização HTTP/1.1
├── test/br/edu/redes/http/
│   ├── TestSupport.java
│   ├── HttpRequestReaderTest.java
│   ├── StaticFileServiceTest.java
│   ├── HttpResponseWriterTest.java
│   └── ServerIntegrationTest.java
├── scripts/
│   ├── compile.sh
│   ├── test.sh
│   ├── run.sh
│   ├── measure-c1.sh
│   └── measure-c2.sh
├── www/
│   ├── index.html
│   ├── style.css
│   └── pixel.png
├── capturas/
│   └── README.md
└── relatorio/
    └── roteiro.md
```

## Ordem de execução e paralelismo

| Onda | Tasks | Regra |
|---|---|---|
| 1 | 1 | Executar sozinha; estabelece compilação e contratos base. |
| 2 | 2, 3, 4 | Podem rodar em paralelo, cada uma em arquivos distintos. |
| 3 | 5 | Só começa depois de 2, 3 e 4 aprovadas. |
| 4 | 6 | Só começa depois de 5; integra persistência e concorrência. |
| 5 | 7, 8 | Podem rodar em paralelo após 6. |
| 6 | 9 | Executar depois de todos os testes locais passarem e em duas máquinas reais. |
| 7 | 10 | Consolidação final depois das capturas e medições. |

---

### Task 1: Esqueleto compilável, configuração e comandos do projeto

**Arquivos:**
- Criar: `.gitignore`
- Criar: `src/br/edu/redes/http/ServerConfig.java`
- Criar: `src/br/edu/redes/http/ServerMain.java`
- Criar: `test/br/edu/redes/http/TestSupport.java`
- Criar: `scripts/compile.sh`
- Criar: `scripts/test.sh`
- Criar: `scripts/run.sh`

**Interfaces:**
- Produz: `record ServerConfig(int port_a, Path root_a, int idleTimeoutMillis_a, int workerCount_a)`.
- Produz: `ServerConfig.i_fromArgs(String[] args_a)`; valida porta `1025..65535`, raiz existente/diretório, timeout positivo e mensagens de erro legíveis.
- Produz: `TestSupport.i_check(boolean condition_a, String message_a)` e `i_expectThrows(Class<? extends Throwable> type_a, ThrowingRunnable action_a)`.
- `ServerMain.main` é apenas o ponto de entrada obrigatório da JVM; toda lógica auxiliar deve ficar em métodos com nomes próprios definidos nesta task.

- [ ] **Passo 1: criar teste executável para argumentos**

Crie em `ServerConfig.java` a API publicada acima. Crie temporariamente `test/br/edu/redes/http/ServerConfigTest.java` com `main` cobrindo: argumentos válidos; porta 1024 rejeitada; porta não numérica rejeitada; `--root` ausente rejeitado; raiz inexistente rejeitada. Use um diretório temporário com `Files.createTempDirectory`.

- [ ] **Passo 2: verificar falha inicial**

Execute `bash scripts/compile.sh` depois de criar o script para compilar `src` em `out/main` e `test` em `out/test`. Resultado esperado: falha porque `ServerConfig.i_fromArgs` ainda não está implementado.

- [ ] **Passo 3: implementar o mínimo**

Use um laço sobre pares de argumentos. Valores padrão: `idleTimeoutMillis_a = 5000`; `workerCount_a = max(4, Runtime.getRuntime().availableProcessors())`. Normalize a raiz com `toAbsolutePath().normalize()` e depois `toRealPath()`. Rejeite opção desconhecida. `ServerMain` apenas lê a configuração e imprime erro/uso em `stderr` com código de saída diferente de zero; o servidor real entra na Task 6.

- [ ] **Passo 4: finalizar scripts e teste**

`compile.sh` deve apagar somente `out/`, recriá-lo e executar `javac -encoding UTF-8 -d out/main` sobre fontes de `src`, depois `javac -encoding UTF-8 -cp out/main -d out/test` sobre fontes de `test`. `test.sh` chama `compile.sh` e executa cada classe `*Test` com `java -ea -cp out/main:out/test`. `run.sh` compila e executa `ServerMain`, repassando `"$@"`.

- [ ] **Passo 5: verificar e versionar**

Execute `bash scripts/test.sh`; esperado: todos os casos de `ServerConfigTest` passam. Execute `bash scripts/run.sh --port 1024 --root ./www`; esperado: erro de porta, sem stack trace. Commit: `chore: create Java server skeleton`.

---

### Task 2: Parser incremental de requisições HTTP

**Arquivos:**
- Criar: `src/br/edu/redes/http/HttpRequest.java`
- Criar: `src/br/edu/redes/http/BadRequestException.java`
- Criar: `src/br/edu/redes/http/HttpRequestReader.java`
- Criar: `test/br/edu/redes/http/HttpRequestReaderTest.java`

**Interfaces:**
- Produz: `record HttpRequest(String method_a, String target_a, String version_a, Map<String,String> headers_a)` com `String i_header(String name_a)` case-insensitive por normalização das chaves para minúsculas.
- Produz: `HttpRequestReader(InputStream input_a, int maxHeaderBytes_a)` e `HttpRequest i_read()`; retorna `null` somente em EOF limpo antes de qualquer byte; lança `BadRequestException` para sintaxe inválida/limite excedido; propaga `SocketTimeoutException`.
- O objeto mantém internamente os bytes excedentes para a próxima chamada de `i_read()`.

- [ ] **Passo 1: escrever casos que falham**

No `main` do teste, cubra com `ByteArrayInputStream`: requisição completa; entrada fragmentada em um `InputStream` que entrega no máximo 2 bytes por leitura; duas requisições concatenadas lidas por duas chamadas; método/alvo/versão ausentes; request line com quatro campos; header sem `:`; CRLF ausente; cabeçalho maior que o limite; nomes de header consultados sem sensibilidade a maiúsculas.

- [ ] **Passo 2: confirmar a falha**

Execute `bash scripts/test.sh`; esperado: compilação ou assertions falham pela ausência do parser.

- [ ] **Passo 3: implementar acumulação de bytes**

Mantenha `byte[] buffer_a`, índices de início/fim e leia até localizar exatamente `\r\n\r\n`. Não converta bytes para texto antes de encontrar o terminador. Ao extrair um bloco, mova apenas o início lógico do buffer; não descarte o restante. Se EOF ocorrer depois de bytes parciais, lance `BadRequestException`.

- [ ] **Passo 4: implementar parsing estrito**

Decodifique cabeçalhos como ISO-8859-1. Separe a request line em exatamente três tokens não vazios. Exija versão `HTTP/1.1`. Em cada linha de header, exija `:` após pelo menos um caractere, aplique `trim` ao valor e `toLowerCase(Locale.ROOT)` ao nome. Rejeite nomes duplicados para manter comportamento inequívoco. O percent-decoding não pertence ao parser; fica na Task 3.

- [ ] **Passo 5: verificar e versionar**

Execute `bash scripts/test.sh`; esperado: todos os casos passam, inclusive duas requisições no mesmo fluxo. Commit: `feat: parse HTTP requests from TCP stream`.

---

### Task 3: Resolução segura de arquivos e tipos MIME

**Arquivos:**
- Criar: `src/br/edu/redes/http/FileResult.java`
- Criar: `src/br/edu/redes/http/StaticFileService.java`
- Criar: `test/br/edu/redes/http/StaticFileServiceTest.java`

**Interfaces:**
- Produz: `record FileResult(int status_a, byte[] body_a, String contentType_a)`.
- Produz: `StaticFileService(Path root_a)` e `FileResult i_get(String requestTarget_a)`.
- Status possíveis nesta camada: `200`, `400`, `403`, `404`.

- [ ] **Passo 1: escrever testes de recurso e segurança**

Crie árvore temporária com `index.html`, `space name.txt`, `image.png`, `unknown.bin` e um arquivo irmão fora da raiz. Cubra `/index.html` = 200; `/space%20name.txt` = 200; arquivo ausente = 404; extensão desconhecida = `application/octet-stream`; query `/index.html?x=1` ignora query; `/../../outside.txt`, `/%2e%2e/%2e%2e/outside.txt` e `/safe/%2e%2e/%2e%2e/outside.txt` = 403; `%ZZ`, byte NUL codificado e target sem `/` = 400.

- [ ] **Passo 2: confirmar a falha**

Execute `bash scripts/test.sh`; esperado: falha pela ausência de `StaticFileService`.

- [ ] **Passo 3: implementar percent-decoding do caminho**

Remova query a partir do primeiro `?`. Decodifique `%HH` manualmente em bytes UTF-8; não use `URLDecoder`, pois ele converte `+` em espaço segundo formulário HTML. Rejeite `%` incompleto, hexadecimal inválido, NUL e UTF-8 inválido com `CharsetDecoder` configurado para `REPORT`.

- [ ] **Passo 4: implementar contenção na raiz**

Remova somente a `/` inicial, resolva contra a raiz e normalize. Antes de ler, exija `candidate_a.startsWith(root_a)`; caso contrário retorne 403. Para arquivo existente, use `toRealPath()` e repita `startsWith(root_a)` para bloquear symlinks que escapam da raiz. Diretórios devem resolver para `index.html`; se o índice não existir, retorne 404. Não liste diretórios.

- [ ] **Passo 5: implementar MIME e verificar**

Mapeie sem diferenciar maiúsculas: `.html -> text/html; charset=utf-8`, `.css -> text/css; charset=utf-8`, `.js -> text/javascript; charset=utf-8`, `.json -> application/json; charset=utf-8`, `.txt -> text/plain; charset=utf-8`, `.png -> image/png`, `.jpg` e `.jpeg -> image/jpeg`, `.pdf -> application/pdf`; fallback `application/octet-stream`. Execute testes. Commit: `feat: serve files within configured root`.

---

### Task 4: Modelo e serialização de respostas HTTP/1.1

**Arquivos:**
- Criar: `src/br/edu/redes/http/HttpResponse.java`
- Criar: `src/br/edu/redes/http/HttpResponseWriter.java`
- Criar: `test/br/edu/redes/http/HttpResponseWriterTest.java`

**Interfaces:**
- Produz: `record HttpResponse(int status_a, String contentType_a, byte[] body_a, boolean close_a, Map<String,String> extraHeaders_a)`.
- Produz: `HttpResponseWriter(String serverName_a, Clock clock_a)` e `void i_write(OutputStream output_a, HttpResponse response_a, boolean headOnly_a)`.
- Razões exatas: `200 OK`, `400 Bad Request`, `403 Forbidden`, `404 Not Found`, `405 Method Not Allowed`.

- [ ] **Passo 1: escrever testes byte a byte**

Use `ByteArrayOutputStream` e `Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC)`. Verifique linha `HTTP/1.1 200 OK\r\n`, `Date: Mon, 21 Sep 2026 12:00:00 GMT`, `Server: Grupo-Redes`, MIME, tamanho em bytes e exatamente um `\r\n\r\n`. Verifique que HEAD tem cabeçalhos idênticos ao GET correspondente e zero bytes depois do separador. Verifique 405 com `Allow: GET, HEAD`. Verifique `Connection: close` somente quando `close_a` for verdadeiro.

- [ ] **Passo 2: confirmar a falha**

Execute `bash scripts/test.sh`; esperado: falha pela ausência do writer.

- [ ] **Passo 3: implementar serialização**

Formate a data com `DateTimeFormatter.RFC_1123_DATE_TIME`, locale inglês e UTC. Escreva cabeçalhos em ASCII, sempre calculando `Content-Length` de `body_a.length`. Ordem fixa: status, `Date`, `Server`, `Content-Length`, `Content-Type`, headers extras, `Connection` se necessário, linha vazia. Para `headOnly_a`, não escreva o corpo.

- [ ] **Passo 4: implementar corpos de erro consistentes**

Adicione `static HttpResponse i_error(int status_a, boolean close_a, Map<String,String> extraHeaders_a)` em `HttpResponse`: corpo UTF-8 simples contendo código e razão, MIME `text/plain; charset=utf-8`. Isso garante `Content-Length` correto também nos erros e permite suprimir somente o corpo em HEAD.

- [ ] **Passo 5: verificar e versionar**

Execute `bash scripts/test.sh`; esperado: testes passam. Commit: `feat: serialize compliant HTTP responses`.

---

### Task 5: Processamento de uma conexão e regras de método

**Arquivos:**
- Criar: `src/br/edu/redes/http/HttpConnectionHandler.java`
- Criar: `test/br/edu/redes/http/ServerIntegrationTest.java`

**Interfaces:**
- Consome: `HttpRequestReader`, `StaticFileService`, `HttpResponseWriter`, `HttpResponse`.
- Produz: `HttpConnectionHandler(Socket socket_a, StaticFileService files_a, HttpResponseWriter writer_a, int idleTimeoutMillis_a)` implementando `Runnable`.
- Regra: HEAD usa a mesma resolução/resposta de GET e passa `headOnly_a=true`; método diferente gera 405 com `Allow`.

- [ ] **Passo 1: criar servidor de teste de uma conexão**

No teste, abra `ServerSocket(0, 1, InetAddress.getLoopbackAddress())`, aceite uma conexão em thread auxiliar e execute o handler. O cliente usa `Socket`, escreve bytes crus e lê resposta usando um helper que respeita `Content-Length`. A porta efêmera é somente para teste; produção continua exigindo porta alta explícita.

- [ ] **Passo 2: cobrir comportamento funcional**

Adicione casos para GET 200 com corpo; HEAD 200 sem corpo e mesmo conjunto/valores de cabeçalhos do GET; POST 405 com `Allow`; request line inválida 400; header inválido 400; arquivo ausente 404; travessia 403. Compare conteúdo binário sem converter para texto.

- [ ] **Passo 3: confirmar falhas**

Execute `bash scripts/test.sh`; esperado: casos de integração falham pela ausência do handler.

- [ ] **Passo 4: implementar roteamento mínimo**

No `run`, configure `socket_a.setSoTimeout(idleTimeoutMillis_a)`. Leia uma requisição, determine `close_a` por comparação case-insensitive do header `Connection` com `close`, resolva GET/HEAD, produza 405 nos demais e escreva a resposta. Converta apenas `BadRequestException` em 400; falha inesperada de I/O encerra a conexão sem tentar escrever uma segunda resposta corrompida.

- [ ] **Passo 5: verificar e versionar**

Execute `bash scripts/test.sh`; esperado: todos os casos passam. Commit: `feat: handle HTTP methods and status codes`.

---

### Task 6: Persistência, timeout, pipelining seguro e concorrência

**Arquivos:**
- Modificar: `src/br/edu/redes/http/HttpConnectionHandler.java`
- Modificar: `src/br/edu/redes/http/ServerMain.java`
- Modificar: `test/br/edu/redes/http/ServerIntegrationTest.java`

**Interfaces:**
- Produz: `static void ServerMain.i_serve(ServerConfig config_a)`.
- Produz: um `HttpRequestReader` por socket, reutilizado durante toda a conexão.
- Encerramento: EOF, `Connection: close`, timeout ocioso, erro de parsing ou desligamento do processo.

- [ ] **Passo 1: adicionar testes de persistência**

No mesmo socket cliente, envie GET 1, leia a resposta, envie GET 2 e leia a segunda. Adicione outro caso enviando duas requisições concatenadas em uma única escrita e confirme duas respostas na ordem. Em um terceiro caso, envie `Connection: close`, confirme esse header na resposta e EOF posterior. Em um quarto, use timeout de 150 ms e confirme EOF após ocioso.

- [ ] **Passo 2: adicionar teste de não bloqueio**

Abra uma conexão A e envie somente metade dos cabeçalhos. Sem fechá-la, abra conexão B, envie GET completo e exija resposta 200 em até 1 s. Este teste deve executar o accept loop real com pelo menos dois workers.

- [ ] **Passo 3: confirmar falhas**

Execute `bash scripts/test.sh`; esperado: persistência e/ou concorrência falham antes da implementação.

- [ ] **Passo 4: implementar laço persistente**

No handler, envolva leitura/processamento/escrita em laço. Reutilize o mesmo reader. Após escrever cada resposta, chame `flush`. Termine se `close_a`; em `SocketTimeoutException`, apenas encerre; em EOF limpo, encerre. Uma requisição inválida recebe 400 com fechamento para evitar dessincronização do fluxo.

- [ ] **Passo 5: implementar servidor concorrente**

Em `i_serve`, crie `ServerSocket`, habilite `setReuseAddress(true)`, faça bind explícito em `new InetSocketAddress("0.0.0.0", port_a)`, e use `Executors.newFixedThreadPool(workerCount_a)`. O accept loop submete um novo handler por conexão. Instale shutdown hook que fecha o `ServerSocket` e chama `shutdownNow` no pool. Não compartilhe buffers ou sockets entre handlers.

- [ ] **Passo 6: verificar e versionar**

Execute `bash scripts/test.sh` três vezes; esperado: nenhuma falha intermitente. Execute localmente `bash scripts/run.sh --port 8080 --root ./www` depois da Task 7 criar `www`, ou use raiz temporária existente. Confirme com `curl -v`. Commit: `feat: add persistent concurrent connections`.

---

### Task 7: Site de interoperabilidade e documentação operacional

**Arquivos:**
- Criar: `www/index.html`
- Criar: `www/style.css`
- Criar: `www/pixel.png`
- Criar: `README.md`
- Criar: `.gitignore` se ainda não existir ou complementar

**Interfaces:**
- Consome: comandos `scripts/compile.sh`, `scripts/test.sh`, `scripts/run.sh`.
- Produz: página HTML que referencia `/style.css` e `/pixel.png`, obrigando o navegador a emitir múltiplas requisições.

- [ ] **Passo 1: criar conteúdo estático mínimo**

`index.html` deve ter HTML5 válido, título do trabalho, uma frase identificando o servidor e `<link rel="stylesheet" href="/style.css">` mais `<img src="/pixel.png" alt="Imagem de teste">`. `style.css` deve tornar visualmente evidente que carregou. `pixel.png` deve ser PNG válido pequeno, não texto renomeado.

- [ ] **Passo 2: escrever README reproduzível**

Documente pré-requisito JDK, compilação, testes e execução exata `bash scripts/run.sh --port 8080 --root ./www`; descoberta do IP; acesso remoto `http://IP_DO_SERVIDOR:8080/`; firewall/porta; argumentos e defaults; estratégia de threads; timeout; comandos curl para 200/400/403/404/405/HEAD/Connection close; aviso de que testes finais usam máquinas distintas.

- [ ] **Passo 3: impedir artefatos na entrega**

Em `.gitignore`, inclua `out/`, `*.class`, `.DS_Store`, arquivos temporários do editor e capturas que não sejam as finais nomeadas. Não ignore `capturas/*.pcapng` porque elas fazem parte da entrega.

- [ ] **Passo 4: verificar e versionar**

Execute testes, inicie o servidor e abra `/` e os dois recursos com curl. Resultado esperado: todos 200, MIME correto e HTML referencia ambos. Commit: `docs: add interoperability site and usage guide`.

---

### Task 8: Clientes de medição C1 e C2

**Arquivos:**
- Criar: `scripts/measure-c1.sh`
- Criar: `scripts/measure-c2.sh`
- Criar: `capturas/README.md`

**Interfaces:**
- Entrada comum: primeiro argumento é URL completa do mesmo recurso, por exemplo `http://192.168.0.10:8080/index.html`.
- C1: exatamente 10 invocações sequenciais de `curl` com `Connection: close`.
- C2: uma única invocação de `curl` com a mesma URL repetida 10 vezes, permitindo reuso da conexão.

- [ ] **Passo 1: criar C1**

Script com `set -eu`, valida um argumento, registra início/fim com nanosegundos quando disponível e executa laço de 1 a 10 usando `curl --silent --show-error --output /dev/null --header 'Connection: close' "$url_a"`. Se qualquer requisição falhar, script encerra com erro.

- [ ] **Passo 2: criar C2**

Script com `set -eu`, valida um argumento e chama um único processo `curl --silent --show-error --output /dev/null` passando a mesma URL 10 vezes. Não usar `Connection: close`. A saída de cada transferência deve ir para `/dev/null`. Um único processo é essencial para o pool de conexões do curl reutilizar o socket.

- [ ] **Passo 3: documentar captura e leitura das métricas**

Em `capturas/README.md`, descreva: iniciar Wireshark na interface física; filtro `tcp.port == 8080`; limpar captura; rodar um cenário; parar; salvar `c1.pcapng`/`c2.pcapng`. Para cada arquivo, usar `Statistics > Conversations > TCP` para pacotes, bytes e duração; usar filtro `tcp.flags.syn == 1 && tcp.flags.ack == 0` para contar aberturas; confirmar cada handshake com SYN, SYN-ACK e ACK. Registrar que retransmissões devem ser mencionadas, não apagadas.

- [ ] **Passo 4: validar sem capturar**

Com servidor local, execute ambos os scripts e confira no log exatamente 10 requisições por cenário; em C1 devem aparecer 10 conexões fechadas, em C2 uma conexão com 10 requisições. Commit: `test: add persistent connection measurement clients`.

---

### Task 9: Execução em rede real e coleta de evidências

**Arquivos:**
- Criar: `capturas/transacao-get.pcapng`
- Criar: `capturas/concorrencia.pcapng`
- Criar: `capturas/c1.pcapng`
- Criar: `capturas/c2.pcapng`
- Criar: `capturas/medicoes.md`

**Interfaces:**
- Requer duas máquinas distintas na mesma rede e Wireshark na interface correta.
- Produz números brutos, sem arredondamento prematuro, para a Task 10.

- [ ] **Passo 1: pré-verificar o ambiente**

Nas duas máquinas, registre sistema, IP e horário. Rode ping entre elas e confirme resposta. Confirme no Wireshark que ICMP/TCP aparece na interface escolhida. Se não houver alcance, pare a coleta e comunique ao professor; não substitua por localhost.

- [ ] **Passo 2: medir RTT**

Do cliente, execute ao menos 10 pings ao IP do servidor. Copie para `medicoes.md` comando, data, IPs, quantidade e RTT mínimo/médio/máximo. O valor usado na análise é o RTT médio.

- [ ] **Passo 3: capturar transação completa**

Com filtro da porta, faça `curl -v -H 'Connection: close' http://IP:PORT/index.html` de outra máquina. Salve `transacao-get.pcapng`. Em `medicoes.md`, anote números dos pacotes de SYN, SYN-ACK, ACK, requisição, resposta e FIN/ACK do encerramento.

- [ ] **Passo 4: capturar simultaneidade**

Inicie uma requisição lenta na máquina A enviando cabeçalhos gradualmente e, antes de terminá-la, requisite `/index.html` na máquina B. Salve `concorrencia.pcapng` e os trechos de log com timestamps/clientes mostrando B respondido enquanto A permanecia incompleta.

- [ ] **Passo 5: executar e capturar C1/C2**

Use o mesmo cliente, servidor, porta, recurso e condições de rede. Para cada cenário, comece captura vazia, rode o script correspondente uma vez, pare e salve. Registre: handshakes completos, total de pacotes, bytes totais e duração total. Confirme C1 = 10 handshakes e C2 = 1, salvo comportamento comprovadamente diferente que deve ser investigado antes de continuar.

- [ ] **Passo 6: calcular métricas derivadas**

Em `medicoes.md`, calcule `economia_pacotes = (pacotes_C1 - pacotes_C2) / pacotes_C1 * 100` e equivalente para bytes. Conte no C1 os pacotes e bytes atribuíveis somente aos 10 handshakes e encerramentos usando os tamanhos exibidos pela captura. Calcule handshakes extras `10 - 1 = 9` e custo teórico mínimo adicional `9 * RTT_médio`; compare com `tempo_C1 - tempo_C2` e explique retransmissões/processamento quando houver diferença.

- [ ] **Passo 7: validar arquivos**

Reabra cada `.pcapng` no Wireshark, confirme que não está vazio e contém somente a coleta pretendida. Commit: `evidence: add network captures and measurements`.

---

### Task 10: Relatório, auditoria de conformidade e pacote final

**Arquivos:**
- Criar: `relatorio/roteiro.md`
- Criar fora do repositório durante exportação e copiar para a raiz de entrega: `relatorio.pdf`
- Modificar: `README.md` somente se a auditoria encontrar instrução incorreta

**Interfaces:**
- Consome: código aprovado, comandos curl do README, `capturas/medicoes.md` e quatro `.pcapng`.
- Produz: conteúdo completo do relatório e checklist de ZIP/TAR.

- [ ] **Passo 1: montar arquitetura e justificativa**

Descreva accept loop, pool fixo, um handler/buffer por conexão, parser incremental, serviço de arquivos, writer e timeout. Justifique pool de threads por simplicidade e isolamento de conexões; declare o limite `workerCount` e que conexão lenta ocupa apenas um worker, não o servidor inteiro.

- [ ] **Passo 2: montar tabela de conformidade**

Inclua uma linha para 200, 400, 403, 404 e 405, cada uma com comando curl reproduzível, primeira linha da resposta e cabeçalhos relevantes. Para 400, use envio bruto que produza header sem `:` ou request line inválida; não alegue que curl gera sintaxe inválida se ele a corrige. Inclua GET e HEAD, comparando headers e ausência de corpo em HEAD.

- [ ] **Passo 3: documentar as três travessias**

Use três formas distintas: `../../`, segmentos mistos e `%2e%2e`. Preserve o caminho no cliente com `curl --path-as-is`; sem isso, curl pode normalizar antes de enviar. Mostre request-target realmente observado e resposta 403 de cada uma.

- [ ] **Passo 4: incorporar evidências de captura e concorrência**

Para a transação completa, inclua imagem/tabela identificando handshake, request, response e fechamento pelos números de pacote. Para simultaneidade, inclua captura/log com IPs e timestamps de ambas as máquinas. Remova ou masque somente dados pessoais que não afetem a prova; mantenha IPs privados necessários à análise.

- [ ] **Passo 5: escrever comparação C1/C2 e análise RTT**

Inclua tabela com handshakes, pacotes, bytes e tempo; economias percentuais de pacotes/bytes; total de pacotes/bytes só de abertura e fechamento no C1; `9 * RTT_médio` como custo de handshake adicional; comparação com diferença medida. Conclusão obrigatória: quanto maior o RTT e/ou o número de recursos/requisições que exigiriam novas conexões, maior tende a ser o ganho da persistência.

- [ ] **Passo 6: fazer auditoria automática e manual**

Execute `bash scripts/test.sh`. Inicie o servidor e repita todos os comandos da tabela. No navegador remoto, abra a página e confirme no painel de rede que HTML, CSS e PNG carregaram. Faça teste cruzado com outro grupo nas duas direções. Cada integrante deve explicar parser de fluxo, contenção de caminhos, HEAD, persistência, timeout e concorrência sem consultar texto pronto.

- [ ] **Passo 7: produzir pacote limpo**

Exporte `roteiro.md` para um único `relatorio.pdf`. Monte ZIP/TAR contendo fontes, scripts, README, `www/`, `capturas/*.pcapng`, `capturas/medicoes.md` e PDF. Exclua `out/`, `.class`, cache, arquivos temporários e metadados do editor. Extraia o pacote em diretório temporário, rode `bash scripts/test.sh`, depois `bash scripts/run.sh --port 8080 --root ./www` e um GET real.

- [ ] **Passo 8: versionar entrega**

Commit: `docs: complete report and delivery evidence`. Registre no README o nome exato do arquivo entregue e lembre que somente um integrante envia no Moodle.

---

## Matriz final de requisitos

| Requisito | Task responsável | Evidência mínima |
|---|---:|---|
| CLI porta/raiz e bind 0.0.0.0 | 1, 6 | execução + inspeção de socket |
| Parser de fluxo, CRLF e sobra | 2 | testes fragmentado e concatenado |
| GET e HEAD | 4, 5 | teste byte a byte + curl |
| 200/400/403/404/405 | 3, 4, 5 | integração + tabela do relatório |
| Content-Length/Type/Date/Server | 3, 4 | teste determinístico do writer |
| Travessia e percent-encoding | 3, 10 | testes + três curls `--path-as-is` |
| Concorrência | 6, 9 | teste de conexão lenta + captura real |
| Persistência, close e timeout | 6 | testes no mesmo socket |
| C1/C2 e RTT | 8, 9 | scripts, pcaps e medições |
| Interoperabilidade no navegador | 7, 10 | HTML + CSS + PNG em outro grupo |
| README, www, capturas e PDF | 7, 9, 10 | pacote reextraído e testado |

## Portões de revisão do agente coordenador

Após cada task, o coordenador deve rejeitar a entrega se qualquer item abaixo falhar:

1. O agente alterou arquivo fora da lista sem justificar conflito de interface.
2. `bash scripts/test.sh` não passa a partir da Task 1.
3. Foi adicionada dependência ou API HTTP de servidor.
4. Um teste de protocolo compara caracteres quando deveria comparar bytes.
5. Um caminho é lido antes das duas verificações de contenção (`normalize` e `toRealPath`).
6. O buffer é recriado entre requisições da mesma conexão ou descarta bytes excedentes.
7. HEAD calcula tamanho zero em vez do tamanho do corpo de GET.
8. A captura/medição usa localhost ou mistura execuções C1 e C2 no mesmo arquivo.

## Definição de pronto

O trabalho só está pronto quando: todos os testes locais passam; todos os curls do relatório foram repetidos; browser remoto carrega HTML/CSS/PNG; duas máquinas são atendidas simultaneamente; C1 possui 10 requisições e C2 possui 10 requisições em uma conexão persistente; quatro capturas abrem no Wireshark; as contas do relatório correspondem aos pcaps; o pacote extraído compila e executa sem arquivos externos.
