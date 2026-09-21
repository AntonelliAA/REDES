package br.edu.redes.http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record ServerConfig(int port_a, Path root_a, int idleTimeoutMillis_a, int workerCount_a) {
    public static ServerConfig i_fromArgs(String[] args_a) {
        if (args_a == null) {
            throw new IllegalArgumentException("argumentos não podem ser nulos");
        }
        Integer port_a = null;
        Path root_a = null;
        int idleTimeoutMillis_a = 5000;
        int workerCount_a = Math.max(4, Runtime.getRuntime().availableProcessors());
        if ((args_a.length & 1) != 0) {
            throw new IllegalArgumentException("opções devem vir em pares");
        }
        for (int index_a = 0; index_a < args_a.length; index_a += 2) {
            String option_a = args_a[index_a];
            String value_a = args_a[index_a + 1];
            if ("--port".equals(option_a)) {
                port_a = i_parseInt(value_a, "--port");
                if (port_a < 1025 || port_a > 65535) {
                    throw new IllegalArgumentException("--port deve estar entre 1025 e 65535");
                }
            } else if ("--root".equals(option_a)) {
                root_a = i_parseRoot(value_a);
            } else if ("--idle-timeout".equals(option_a)) {
                idleTimeoutMillis_a = i_parseInt(value_a, "--idle-timeout");
                if (idleTimeoutMillis_a <= 0) {
                    throw new IllegalArgumentException("--idle-timeout deve ser positivo");
                }
            } else if ("--workers".equals(option_a)) {
                workerCount_a = i_parseInt(value_a, "--workers");
                if (workerCount_a <= 0) {
                    throw new IllegalArgumentException("--workers deve ser positivo");
                }
            } else {
                throw new IllegalArgumentException("opção desconhecida: " + option_a);
            }
        }
        if (port_a == null) {
            throw new IllegalArgumentException("--port é obrigatório");
        }
        if (root_a == null) {
            throw new IllegalArgumentException("--root é obrigatório");
        }
        return new ServerConfig(port_a, root_a, idleTimeoutMillis_a, workerCount_a);
    }

    private static int i_parseInt(String value_a, String option_a) {
        try {
            return Integer.parseInt(value_a);
        } catch (NumberFormatException exception_a) {
            throw new IllegalArgumentException(option_a + " deve ser um número inteiro", exception_a);
        }
    }

    private static Path i_parseRoot(String value_a) {
        Path root_a = Path.of(value_a).toAbsolutePath().normalize();
        try {
            root_a = root_a.toRealPath();
        } catch (IOException exception_a) {
            throw new IllegalArgumentException("raiz inexistente ou inacessível: " + value_a, exception_a);
        }
        if (!Files.isDirectory(root_a)) {
            throw new IllegalArgumentException("raiz não é um diretório: " + value_a);
        }
        return root_a;
    }
}
