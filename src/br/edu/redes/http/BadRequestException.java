package br.edu.redes.http;

public class BadRequestException extends Exception {
    public BadRequestException(String mensagem) {
        super(mensagem);
    }

    public BadRequestException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
