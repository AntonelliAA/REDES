package br.edu.redes.http;

import java.nio.charset.StandardCharsets;
import java.nio.channels.SeekableByteChannel;
import java.util.Collections;
import java.util.Map;

public record HttpResponse(int status, String tipoConteudo, byte[] corpo, boolean fecharConexao,
                           Map<String, String> cabecalhosExtras, long tamanhoConteudo, SeekableByteChannel arquivo) {
    public HttpResponse(int status, String tipoConteudo, byte[] corpo, boolean fecharConexao,
                        Map<String, String> cabecalhosExtras) {
        this(status, tipoConteudo, corpo, fecharConexao, cabecalhosExtras,
                corpo == null ? 0 : corpo.length, null);
    }

    public HttpResponse {
        if (corpo == null) {
            corpo = new byte[0];
        }
        if (cabecalhosExtras == null) {
            cabecalhosExtras = Collections.emptyMap();
        } else {
            cabecalhosExtras = Map.copyOf(cabecalhosExtras);
        }
    }

    public static HttpResponse deArquivo(FileResult resultado, boolean fecharConexao) {
        return new HttpResponse(200, resultado.tipoConteudo(), null, fecharConexao,
                Map.of(), resultado.tamanho(), resultado.canal());
    }

    public static String obterRazaoStatus(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "Status " + status;
        };
    }

    public static HttpResponse erro(int status, boolean fecharConexao, Map<String, String> cabecalhosExtras) {
        String razao = obterRazaoStatus(status);
        byte[] corpoErro = (status + " " + razao + "\r\n").getBytes(StandardCharsets.UTF_8);
        return new HttpResponse(status, "text/plain; charset=utf-8", corpoErro, fecharConexao, cabecalhosExtras);
    }
}
