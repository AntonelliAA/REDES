package br.edu.redes.http;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerConfigTest {
    private ServerConfigTest() {
    }

    public static void main(String[] args_a) throws Exception {
        Path root_a = Files.createTempDirectory("http-root-");
        ServerConfig config_a = ServerConfig.i_fromArgs(new String[]{"--port", "8080", "--root", root_a.toString()});
        TestSupport.i_check(config_a.port_a() == 8080, "porta válida");
        TestSupport.i_check(config_a.root_a().equals(root_a.toRealPath()), "raiz normalizada");
        TestSupport.i_check(config_a.idleTimeoutMillis_a() == 5000, "timeout padrão");
        TestSupport.i_check(config_a.workerCount_a() >= 4, "workers padrão");
        TestSupport.i_expectThrows(IllegalArgumentException.class, () -> ServerConfig.i_fromArgs(new String[]{"--port", "1024", "--root", root_a.toString()}));
        TestSupport.i_expectThrows(IllegalArgumentException.class, () -> ServerConfig.i_fromArgs(new String[]{"--port", "abc", "--root", root_a.toString()}));
        TestSupport.i_expectThrows(IllegalArgumentException.class, () -> ServerConfig.i_fromArgs(new String[]{"--port", "8080"}));
        TestSupport.i_expectThrows(IllegalArgumentException.class, () -> ServerConfig.i_fromArgs(new String[]{"--port", "8080", "--root", root_a.resolve("missing").toString()}));
        System.out.println("ServerConfigTest: OK");
    }
}

