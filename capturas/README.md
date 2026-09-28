# Capturas e medições do trabalho

As evidências devem ser obtidas entre máquinas distintas. Os arquivos `.pcapng` e os valores medidos ainda precisam ser produzidos no ambiente do grupo.

## Preparação

1. Registre data, sistemas operacionais, IPs de servidor e clientes e porta usada em `medicoes.md`.
2. No cliente, execute `ping -c 10 <IP_DO_SERVIDOR>` (Linux/macOS) ou `ping -n 10 <IP_DO_SERVIDOR>` (Windows). Salve a saída e registre o RTT médio.
3. No Wireshark, selecione a interface Wi-Fi/Ethernet usada na comunicação. O **filtro de captura** é `tcp port 8080`. O **filtro de exibição** é `tcp.port == 8080`.
4. Use um arquivo de captura novo por cenário, sem outras requisições à porta. Inicie a captura antes do script e pare somente após o encerramento TCP. Limpar o filtro de exibição não apaga os pacotes capturados.

## Coleta

A partir da raiz do projeto na máquina cliente:

```bash
bash scripts/measure-c1.sh http://<IP_DO_SERVIDOR>:8080/index.html
```

Salve como `capturas/c1.pcapng`. Em uma nova captura, execute:

```bash
bash scripts/measure-c2.sh http://<IP_DO_SERVIDOR>:8080/index.html
```

Salve como `capturas/c2.pcapng`. Ambos fazem 10 GETs sequenciais com HTTP/1.1, sem proxy. C1 envia `Connection: close` em todos; C2 mantém a conexão e o curl a encerra ao terminar. Os scripts falham se não receberem 200 ou se a quantidade de conexões não corresponder ao cenário.

Produza também:

- `transacao-get.pcapng`: execute `curl --http1.1 -i -H 'Connection: close' http://<IP_DO_SERVIDOR>:8080/index.html` de outra máquina. Identifique abertura TCP, requisição, resposta e encerramento.
- `concorrencia.pcapng` ou log equivalente: duas máquinas clientes requisitam simultaneamente. Registre os dois IPs, horários e respostas. Para tornar a sobreposição visível, uma máquina pode manter uma requisição parcial enquanto a outra realiza um GET completo; depois, conclua a primeira requisição antes do timeout. Ambas devem receber resposta.
- Evidência de interoperabilidade: navegador de outra máquina com a página, CSS e imagem carregados.

## Extração das quatro métricas

Aplique o filtro de exibição da porta e dos IPs envolvidos para isolar o cenário.

1. **Handshakes completos:** `tcp.flags.syn == 1 && tcp.flags.ack == 0` ajuda a localizar conexões, mas SYN sozinho não comprova handshake completo. Em cada `tcp.stream`, confira SYN, SYN/ACK e ACK final, sem contar retransmissões como novas conexões. Esperado: 10 em C1 e 1 em C2.
2. **Pacotes e bytes totais:** em `Statistics > Conversations > TCP`, limite aos pacotes exibidos e some as linhas do cenário em ambas as direções. Use a mesma definição de bytes nos dois casos; por exemplo, a soma do tamanho dos quadros (`frame.len`).
3. **Tempo total:** subtraia o horário do primeiro pacote do cenário do horário do último pacote, incluindo o encerramento. Em C1, não use só a duração de uma conversa nem some as durações individuais: o intervalo total inclui os espaços entre conexões.
4. **Economia percentual de pacotes e bytes:** `100 × (C1 − C2) / C1`.

## Overhead de abertura e encerramento

Em C1, identifique os pacotes usados para abrir e fechar cada conexão. Registre os números dos quadros e some os respectivos tamanhos. Inclua os ACKs dessas etapas quando forem pacotes separados. Não some todos os ACKs da captura: muitos confirmam dados HTTP.

O ACK final do handshake pode carregar a requisição, e um FIN pode acompanhar dados ou um ACK de outra etapa. Registre essa combinação ao separar overhead de controle e tráfego útil. Não assuma que cada conexão custou exatamente 3 + 4 pacotes nem calcule bytes somando apenas SYN e FIN.

Preencha `medicoes.md` e use os valores e quadros observados no relatório. O modelo `9 × RTT` estima o custo dos nove handshakes adicionais de C1; compare-o com `tempo_C1 − tempo_C2` e explique diferenças observadas.
