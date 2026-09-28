# Relatório Técnico — Servidor HTTP/1.1 sobre Sockets TCP

**Disciplina:** Laboratório de Redes de Computadores  
**Trabalho 1:** Implementação e análise de conexões persistentes  
**Integrantes:** Anthony Antonelli, [completar conforme o grupo no Moodle]  
**Data dos testes:** [preencher]

> Roteiro de relatório: faltam os resultados reais da rede, as capturas e a revisão final. Substitua os campos entre colchetes e exporte este documento para um único PDF antes da entrega. Não apresente valores esperados como medições realizadas.

## 1. Arquitetura e concorrência

O servidor utiliza `java.net.ServerSocket` e `java.net.Socket`, sem biblioteca HTTP de servidor. `ServerConfig` valida porta, raiz, timeout e quantidade de threads. `ServerMain` escuta em `0.0.0.0` e entrega os sockets aceitos a um pool fixo de threads.

`HttpRequestReader` acumula bytes até `\r\n\r\n` e conserva o excedente para a próxima requisição. `StaticFileService` decodifica percent-encoding e verifica que os caminhos normalizados e reais permanecem na raiz, incluindo a resolução de links simbólicos. `HttpResponseWriter` escreve a resposta HTTP/1.1 com `Date` em GMT, `Server`, `Content-Type` e `Content-Length`; HEAD omite o corpo correspondente ao GET. `HttpConnectionHandler` atende requisições sucessivas até `Connection: close`, timeout ocioso ou encerramento do cliente.

Corpos de requisição não são processados: `Content-Length` positivo ou `Transfer-Encoding` fazem a resposta encerrar a conexão, evitando confundir o corpo com uma próxima requisição. `Content-Length: 0` permite persistência. Métodos diferentes de GET e HEAD recebem 405.

O pool fixo limita o número de threads e mantém o código simples. Uma conexão lenta ocupa uma thread; outras podem progredir enquanto houver threads livres. Quando todas estão ocupadas, as novas conexões aguardam. Essa escolha atende ao teste de concorrência com pelo menos dois workers, mas não constitui proteção completa contra esgotamento de recursos.

Configuração usada: porta [preencher], raiz [preencher], workers [preencher], timeout [preencher] ms.

## 2. Conformidade HTTP

No cliente, defina `BASE='http://<IP_DO_SERVIDOR>:8080'`, substituindo o IP, e registre as respostas obtidas:

| Caso | Comando | Resultado esperado | Resposta observada |
| :--- | :--- | :--- | :--- |
| GET | `curl --http1.1 -i "$BASE/index.html"` | `200 OK` e corpo do arquivo | [preencher] |
| HEAD | `curl --http1.1 -I "$BASE/index.html"` | `200 OK`, mesmo tamanho do GET, sem corpo | [preencher] |
| Linha inválida | `curl --http1.1 -i --request-target '/ alvo-invalido' "$BASE/"` | `400 Bad Request` | [preencher] |
| Travessia | `curl --http1.1 --path-as-is -i "$BASE/../../etc/passwd"` | `403 Forbidden` | [preencher] |
| Arquivo inexistente | `curl --http1.1 -i "$BASE/arquivo-inexistente.html"` | `404 Not Found` | [preencher] |
| POST | `curl --http1.1 -i -X POST "$BASE/index.html"` | `405 Method Not Allowed`, `Allow: GET, HEAD` | [preencher] |

Registre os cabeçalhos de cada resposta, inclusive `Content-Length` real. O tamanho de `index.html` muda quando o arquivo é editado. Em GET e HEAD, compare tipo e tamanho; o valor de `Date` pode mudar entre requisições feitas em segundos diferentes.

## 3. Segurança: três tentativas de travessia

Use `--path-as-is` para que o curl não normalize o caminho antes do envio.

| Tentativa | Comando | Resposta observada |
| :--- | :--- | :--- |
| `../` direto | `curl --http1.1 --path-as-is -i "$BASE/../../etc/passwd"` | [registrar; esperado 403] |
| Percent-encoding | `curl --http1.1 --path-as-is -i "$BASE/%2e%2e/%2e%2e/outside.txt"` | [registrar; esperado 403] |
| Caminho misto | `curl --http1.1 --path-as-is -i "$BASE/safe/%2e%2e/%2e%2e/outside.txt"` | [registrar; esperado 403] |

A terceira tentativa não exige que `safe/` exista: a normalização já tenta ultrapassar a raiz. Anexe as requisições e respostas reais que demonstram as três rejeições.

## 4. Transação GET completa de outra máquina

Arquivo: `capturas/transacao-get.pcapng` [confirmar]. IP cliente [preencher]; IP servidor [preencher]. Comando: `curl --http1.1 -i -H 'Connection: close' "$BASE/index.html"`.

| Etapa | Quadros identificados na captura |
| :--- | :--- |
| SYN, SYN/ACK e ACK do handshake | [preencher] |
| Requisição GET | [preencher] |
| Cabeçalhos e corpo da resposta 200 | [preencher] |
| Encerramento TCP e confirmações | [preencher] |

Insira uma imagem legível da captura. Use os números reais dos quadros: ACKs e FINs podem estar combinados com outros dados, e a resposta pode ocupar vários pacotes.

## 5. Atendimento simultâneo e interoperabilidade

Captura ou log: [preencher]. Clientes distintos: IP A [preencher] e IP B [preencher]. Registre os horários sobrepostos das requisições e as respostas recebidas por ambos, usando o procedimento de `capturas/README.md`.

Resultado observado: [descrever com base na evidência]. Um teste local automatizado não substitui essa evidência entre máquinas distintas.

No navegador de outra máquina, abra a página inicial e verifique HTML, `style.css` e `pixel.png`. Registre o resultado e as máquinas/grupos envolvidos: [preencher].

## 6. RTT e comparação C1 × C2

Ambiente, IPs e saída do ping: [preencher]. **RTT médio:** [preencher] ms. Recurso comum aos dois cenários: [preencher], tamanho [preencher] bytes. Execute 10 requisições sequenciais por cenário e salve `capturas/c1.pcapng` e `capturas/c2.pcapng`.

| Métrica da captura | C1: conexão nova por GET | C2: uma conexão persistente |
| :--- | :---: | :---: |
| Handshakes TCP completos | [medir; esperado 10] | [medir; esperado 1] |
| Total de pacotes, ambas as direções | [medir] | [medir] |
| Total de bytes, ambas as direções | [medir] | [medir] |
| Tempo do primeiro ao último pacote (ms) | [medir] | [medir] |

Critério para contagem dos bytes e quadros usados no intervalo de tempo: [preencher].

- Economia de pacotes: `100 × (pacotes_C1 − pacotes_C2) / pacotes_C1` = [calcular] %.
- Economia de bytes: `100 × (bytes_C1 − bytes_C2) / bytes_C1` = [calcular] %.

## 7. Overhead TCP e relação com o RTT

Na captura C1, identifique os pacotes exclusivamente usados na abertura e no encerramento das conexões, incluindo os ACKs dessas etapas quando forem separados. Some seus tamanhos segundo o mesmo critério de bytes da seção anterior. Explique quadros que também carreguem dados e evite contagem duplicada.

| Etapa em C1 | Quadros | Pacotes | Bytes |
| :--- | :--- | :---: | :---: |
| Abertura | [preencher] | [medir] | [medir] |
| Encerramento | [preencher] | [medir] | [medir] |
| Total | — | [somar] | [somar] |

Não substitua essa contagem por um valor fixo de 70 pacotes: a combinação de ACKs, FINs, dados e eventuais retransmissões altera o total observado.

No modelo de TCP sem perdas, cada nova conexão exige aproximadamente um RTT de estabelecimento antes do envio normal da requisição. C1 abre nove conexões a mais que C2, portanto o custo adicional esperado dos handshakes é aproximadamente `9 × RTT médio` = [calcular] ms.

A diferença de tempo medida foi `tempo_C1 − tempo_C2` = [calcular] ms, equivalente a [calcular] RTTs. Compare o resultado com os nove RTTs do modelo e discuta o que a captura mostra: processamento, intervalos entre requisições, encerramentos e eventuais perdas também afetam o tempo total.

## 8. Conclusão

Resultado observado e sustentado pelas métricas: [preencher].

Para o mesmo recurso e número de requisições sequenciais, o ganho esperado da persistência aumenta em redes com RTT maior, pois evita nove novos handshakes neste experimento. Por exemplo, no modelo com RTT de 100 ms, a economia de estabelecimento seria de cerca de 900 ms. Esse exemplo é teórico; a conclusão experimental deve usar o RTT e as capturas do grupo.
