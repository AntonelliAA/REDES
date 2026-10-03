# Registro das medições

Testes de 3 de outubro de 2026. Servidor em um MacBook (macOS), porta 8080, raiz `./www`. Cliente principal em Windows, com `curl.exe`. As capturas foram feitas no servidor e contêm só os pacotes de `tcp.port == 8080`.

## RTT

`ping -c 10` entre as duas máquinas, sem perda: mínimo 2,756 ms, **médio 3,918 ms**, máximo 7,730 ms, desvio padrão 1,907 ms.

## C1 e C2

Dez GETs sequenciais de `/index.html` (2294 bytes). Bytes = soma de `frame.len` nos dois sentidos. Tempo = do primeiro SYN ao último ACK do encerramento.

| Métrica | C1 (`c1.pcapng`) | C2 (`c2.pcapng`) | Economia |
| :--- | :---: | :---: | :---: |
| Handshakes completos | 10 | 1 | — |
| Total de pacotes | 120 | 67 | 44,2 % |
| Total de bytes | 32 630 | 29 070 | 10,9 % |
| Tempo total (ms) | 105,6 | 64,5 | 39,0 % |

Sem retransmissões e sem RST nas duas capturas.

## Overhead de conexão em C1

Cada uma das dez conexões tem 12 pacotes e 3263 bytes.

| Etapa | Por conexão | Pacotes (total) | Bytes (total) |
| :--- | :--- | :---: | :---: |
| Abertura (SYN, SYN/ACK, ACK) | 3 pacotes, 192 bytes | 30 | 1920 |
| Encerramento (FIN, ACK, FIN, ACK) | 4 pacotes, 228 bytes | 40 | 2280 |
| Total | 7 pacotes, 420 bytes | 70 | 4200 |

O ACK do cliente no encerramento confirma também os últimos dados; foi contado como encerramento.

## Tempo e RTT

- Diferença medida: `tempo_C1 − tempo_C2` = 41,2 ms (10,5 RTTs do ping).
- Previsão com o RTT do ping: 9 × 3,918 = 35,3 ms.
- RTT médio dos dez handshakes de C1 (SYN/ACK até o ACK): 4,484 ms. Previsão: 9 × 4,484 = 40,4 ms.

## Outras evidências

- Transação GET completa: quadros 1 a 11 e 13 de `c1.pcapng` (handshake 1 a 3, requisição e resposta 4 a 8, encerramento 9 a 13).
- Concorrência: `concorrencia.pcapng`. O cliente A mantém uma requisição parcial de 0,4 s a 10,4 s; o cliente B, de outro IP, é atendido por inteiro em 4,92 s a 4,98 s. Os dois recebem 200.
