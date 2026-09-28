package br.edu.redes.http;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] argumentos) {
        try {
            ServerConfig.fromArgs(argumentos);
        } catch (IllegalArgumentException e) {
            System.err.println("Erro: " + e.getMessage());
            System.err.println("Uso: --port <1025..65535> --root <diretório> [--idle-timeout <ms>] [--workers <quantidade>]");
            System.exit(2);
        }
    }
}
