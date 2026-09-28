# Registro de medições reais

**Pendente:** preencher durante os testes entre máquinas distintas. Valores esperados e estimativas teóricas não são resultados medidos.

## Ambiente e RTT

- Data e horário: [preencher]
- Servidor: sistema operacional [preencher], IP [preencher], porta [preencher]
- Cliente C1/C2: sistema operacional [preencher], IP [preencher]
- Segundo cliente para concorrência: sistema operacional [preencher], IP [preencher]
- Recurso solicitado e tamanho em bytes: [preencher]
- Comando de ping: [preencher]
- Saída completa do ping ou arquivo com a saída: [preencher]
- RTT mínimo / **médio** / máximo: [preencher] ms

## C1 e C2

| Métrica extraída da captura | C1 | C2 | Economia (%) |
| :--- | :---: | :---: | :---: |
| Handshakes completos | [medir; esperado 10] | [medir; esperado 1] | [calcular] |
| Total de pacotes, ambas as direções | [medir] | [medir] | [calcular] |
| Total de bytes, ambas as direções | [medir] | [medir] | [calcular] |
| Tempo do primeiro ao último pacote (ms) | [medir] | [medir] | [calcular] |

- Arquivos: `c1.pcapng` e `c2.pcapng` [confirmar que foram salvos]
- Filtro e critério de contagem de bytes: [preencher]
- Quadros inicial/final e respectivos horários, em cada cenário: [preencher]
- Economia: `100 × (C1 − C2) / C1`.

## Overhead medido em C1

| Etapa | Números dos quadros de controle | Pacotes | Bytes |
| :--- | :--- | :---: | :---: |
| Abertura das conexões | [preencher] | [medir] | [medir] |
| Encerramento das conexões | [preencher] | [medir] | [medir] |
| Total | — | [somar] | [somar] |

Inclua ACKs exclusivos de abertura/encerramento; explique pacotes que também transportem dados e não os conte duas vezes. Descreva retransmissões, caso existam.

- Estimativa do custo dos handshakes adicionais: `9 × RTT médio` = [calcular] ms.
- Diferença observada: `tempo_C1 − tempo_C2` = [calcular] ms.
- Diferença observada em RTTs: `(tempo_C1 − tempo_C2) / RTT médio` = [calcular].
- Explicação de diferenças entre modelo e medição: [preencher].

## Outras evidências obrigatórias

- Respostas dos comandos de conformidade e três travessias: [anexar saída real].
- Transação GET de outra máquina: arquivo [preencher], quadros de abertura/requisição/resposta/encerramento [preencher].
- Concorrência: captura ou log [preencher], dois IPs [preencher], intervalos sobrepostos e respostas [preencher].
- Interoperabilidade no navegador: máquinas/grupos [preencher], HTML/CSS/imagem carregados [verificar].
