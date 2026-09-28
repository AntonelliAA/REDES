package br.edu.redes.http;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public record HttpRequest(String metodo, String alvo, String versao, Map<String, String> cabecalhos) {
    public HttpRequest {
        cabecalhos = Collections.unmodifiableMap(cabecalhos);
    }

    public String obterCabecalho(String nome) {
        if (nome == null) {
            return null;
        }
        return cabecalhos.get(nome.toLowerCase(Locale.ROOT));
    }
}
