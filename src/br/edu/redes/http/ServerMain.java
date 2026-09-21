package br.edu.redes.http;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args_a) {
        try {
            ServerConfig.i_fromArgs(args_a);
        } catch (IllegalArgumentException exception_a) {
            System.err.println("Erro: " + exception_a.getMessage());
            System.err.println("Uso: --port <1025..65535> --root <diretório> [--idle-timeout <ms>] [--workers <quantidade>]");
            System.exit(2);
        }
    }
}

