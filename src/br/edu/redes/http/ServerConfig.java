package br.edu.redes.http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record ServerConfig(int porta, Path raiz, int tempoLimiteOciosoMs, int quantidadeTrabalhadores) {
    public ServerConfig {
        if (quantidadeTrabalhadores < 2) {
            throw new IllegalArgumentException("A quantidade de workers deve ser pelo menos 2 para atender clientes simultaneamente.");
        }
    }

    public static ServerConfig fromArgs(String[] argumentos) {
        if (argumentos == null) {
            throw new IllegalArgumentException("Os argumentos não podem ser nulos.");
        }
        if (argumentos.length % 2 != 0) {
            throw new IllegalArgumentException("As opções devem vir em pares chave-valor.");
        }

        Integer porta = null;
        Path raiz = null;
        int tempoLimiteOciosoMs = 5000;
        int quantidadeTrabalhadores = Math.max(4, Runtime.getRuntime().availableProcessors());

        for (int i = 0; i < argumentos.length; i += 2) {
            String opcao = argumentos[i];
            String valor = argumentos[i + 1];

            switch (opcao) {
                case "--port" -> {
                    porta = converterInteiro(valor, "--port");
                    if (porta < 1025 || porta > 65535) {
                        throw new IllegalArgumentException("A porta deve estar entre 1025 e 65535.");
                    }
                }
                case "--root" -> raiz = validarDiretorioRaiz(valor);
                case "--idle-timeout" -> {
                    tempoLimiteOciosoMs = converterInteiro(valor, "--idle-timeout");
                    if (tempoLimiteOciosoMs <= 0) {
                        throw new IllegalArgumentException("O timeout de conexão ociosa deve ser positivo.");
                    }
                }
                case "--workers" -> {
                    quantidadeTrabalhadores = converterInteiro(valor, "--workers");
                }
                default -> throw new IllegalArgumentException("Opção desconhecida: " + opcao);
            }
        }

        if (porta == null) {
            throw new IllegalArgumentException("O parâmetro --port é obrigatório.");
        }
        if (raiz == null) {
            throw new IllegalArgumentException("O parâmetro --root é obrigatório.");
        }

        return new ServerConfig(porta, raiz, tempoLimiteOciosoMs, quantidadeTrabalhadores);
    }

    private static int converterInteiro(String valor, String opcao) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(opcao + " deve ser um número inteiro válido.", e);
        }
    }

    private static Path validarDiretorioRaiz(String caminhoTexto) {
        Path caminho = Path.of(caminhoTexto).toAbsolutePath().normalize();
        try {
            caminho = caminho.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Diretório raiz inexistente ou inacessível: " + caminhoTexto, e);
        }
        if (!Files.isDirectory(caminho)) {
            throw new IllegalArgumentException("O caminho especificado não é um diretório: " + caminhoTexto);
        }
        return caminho;
    }
}
