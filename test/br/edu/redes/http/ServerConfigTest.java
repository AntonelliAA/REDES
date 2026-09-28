package br.edu.redes.http;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerConfigTest {
    private ServerConfigTest() {
    }

    public static void main(String[] args) throws Exception {
        Path diretorioRaiz = Files.createTempDirectory("http-raiz-");
        ServerConfig config = ServerConfig.fromArgs(new String[]{"--port", "8080", "--root", diretorioRaiz.toString()});

        TestSupport.checar(config.porta() == 8080, "porta válida");
        TestSupport.checar(config.raiz().equals(diretorioRaiz.toRealPath()), "raiz normalizada");
        TestSupport.checar(config.tempoLimiteOciosoMs() == 5000, "timeout padrão");
        TestSupport.checar(config.quantidadeTrabalhadores() >= 4, "workers padrão");

        TestSupport.esperarExcecao(IllegalArgumentException.class, () -> ServerConfig.fromArgs(new String[]{"--port", "1024", "--root", diretorioRaiz.toString()}));
        TestSupport.esperarExcecao(IllegalArgumentException.class, () -> ServerConfig.fromArgs(new String[]{"--port", "abc", "--root", diretorioRaiz.toString()}));
        TestSupport.esperarExcecao(IllegalArgumentException.class, () -> ServerConfig.fromArgs(new String[]{"--port", "8080"}));
        TestSupport.esperarExcecao(IllegalArgumentException.class, () -> ServerConfig.fromArgs(new String[]{"--port", "8080", "--root", diretorioRaiz.resolve("missing").toString()}));

        System.out.println("ServerConfigTest: OK");
    }
}
