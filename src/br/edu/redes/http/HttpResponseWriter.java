package br.edu.redes.http;

import java.io.IOException;
import java.io.EOFException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
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

        cabecalhos.append("HTTP/1.1 ").append(resposta.status()).append(" ").append(razao).append("\r\n");

        String dataFormatada = FORMATADOR_DATA.format(relogio.instant());
        cabecalhos.append("Date: ").append(dataFormatada).append("\r\n");

        cabecalhos.append("Server: ").append(nomeServidor).append("\r\n");

        cabecalhos.append("Content-Length: ").append(resposta.tamanhoConteudo()).append("\r\n");

        if (resposta.tipoConteudo() != null && !resposta.tipoConteudo().isBlank()) {
            cabecalhos.append("Content-Type: ").append(resposta.tipoConteudo()).append("\r\n");
        }

        for (Map.Entry<String, String> entrada : resposta.cabecalhosExtras().entrySet()) {
            cabecalhos.append(entrada.getKey()).append(": ").append(entrada.getValue()).append("\r\n");
        }

        if (resposta.fecharConexao()) {
            cabecalhos.append("Connection: close\r\n");
        }

        cabecalhos.append("\r\n");

        saida.write(cabecalhos.toString().getBytes(StandardCharsets.ISO_8859_1));

        if (!apenasCabecalhos) {
            if (resposta.arquivo() != null) {
                ByteBuffer bloco = ByteBuffer.allocate(16 * 1024);
                long restante = resposta.tamanhoConteudo();
                while (restante > 0) {
                    bloco.clear();
                    bloco.limit((int) Math.min(bloco.capacity(), restante));
                    int lidos = resposta.arquivo().read(bloco);
                    if (lidos < 0) {
                        throw new EOFException("Arquivo terminou antes do Content-Length anunciado.");
                    }
                    saida.write(bloco.array(), 0, lidos);
                    restante -= lidos;
                }
            } else if (resposta.corpo().length > 0) {
                saida.write(resposta.corpo());
            }
        }

        saida.flush();
    }
}
