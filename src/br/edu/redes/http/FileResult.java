package br.edu.redes.http;

public record FileResult(int status, byte[] corpo, String tipoConteudo) {
}
