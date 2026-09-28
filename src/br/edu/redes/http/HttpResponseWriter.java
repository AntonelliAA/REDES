package br.edu.redes.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public class HttpResponseWriter {
    private static final DateTimeFormatter FORMATADOR_DATA = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);

    private final String nomeServidor;
    private final Clock relogio;

    public HttpResponseWriter(String nomeServidor, Clock relogio) {
        if (nomeServidor == null || nomeServidor.isBlank()) {
            throw new IllegalArgumentException("O identificador do servidor não pode ser nulo ou vazio.");
        }
        if (relogio == null) {
            throw new IllegalArgumentException("O relógio não pode ser nulo.");
        }
        this.nomeServidor = nomeServidor;
        this.relogio = relogio;
    }

    public void escrever(OutputStream saida, HttpResponse resposta, boolean apenasCabecalhos) throws IOException {
        if (saida == null || resposta == null) {
            throw new IllegalArgumentException("Saída e resposta não podem ser nulas.");
        }

        StringBuilder cabecalhos = new StringBuilder();
        String razao = HttpResponse.obterRazaoStatus(resposta.status());

        // 1. Linha de status
        cabecalhos.append("HTTP/1.1 ").append(resposta.status()).append(" ").append(razao).append("\r\n");

        // 2. Data no formato IMF-fixdate GMT conforme RFC 9110
        String dataFormatada = FORMATADOR_DATA.format(relogio.instant());
        cabecalhos.append("Date: ").append(dataFormatada).append("\r\n");

        // 3. Identificador Server
        cabecalhos.append("Server: ").append(nomeServidor).append("\r\n");

        // 4. Content-Length (sempre calculado sobre o tamanho real do corpo)
        cabecalhos.append("Content-Length: ").append(resposta.corpo().length).append("\r\n");

        // 5. Content-Type se presente
        if (resposta.tipoConteudo() != null && !resposta.tipoConteudo().isBlank()) {
            cabecalhos.append("Content-Type: ").append(resposta.tipoConteudo()).append("\r\n");
        }

        // 6. Cabeçalhos adicionais (ex: Allow)
        for (Map.Entry<String, String> entrada : resposta.cabecalhosExtras().entrySet()) {
            cabecalhos.append(entrada.getKey()).append(": ").append(entrada.getValue()).append("\r\n");
        }

        // 7. Connection: close se a conexão for encerrar
        if (resposta.fecharConexao()) {
            cabecalhos.append("Connection: close\r\n");
        }

        // 8. Linha em branco que finaliza a seção de cabeçalhos
        cabecalhos.append("\r\n");

        // Escrever cabeçalhos em ISO-8859-1
        saida.write(cabecalhos.toString().getBytes(StandardCharsets.ISO_8859_1));

        // Escrever corpo da mensagem se não for requisição HEAD
        if (!apenasCabecalhos && resposta.corpo().length > 0) {
            saida.write(resposta.corpo());
        }

        saida.flush();
    }
}
