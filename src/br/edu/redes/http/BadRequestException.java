package br.edu.redes.http;

public class BadRequestException extends Exception {
    private final boolean apenasCabecalhos;

    public BadRequestException(String mensagem) {
        this(mensagem, null, false);
    }

    public BadRequestException(String mensagem, Throwable causa) {
        this(mensagem, causa, false);
    }

    public BadRequestException(String mensagem, Throwable causa, boolean apenasCabecalhos) {
        super(mensagem, causa);
        this.apenasCabecalhos = apenasCabecalhos;
    }

    public boolean apenasCabecalhos() {
        return apenasCabecalhos;
    }
}
