package br.edu.redes.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] argumentos) {
        try {
            ServerConfig config = ServerConfig.fromArgs(argumentos);
            iniciarServidor(config);
        } catch (IllegalArgumentException e) {
            System.err.println("Erro: " + e.getMessage());
            System.err.println("Uso: --port <1025..65535> --root <diretório> [--idle-timeout <ms>] [--workers <2 ou mais>]");
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Erro de E/S ao iniciar servidor: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void iniciarServidor(ServerConfig config) throws IOException {
        StaticFileService servicoArquivos = new StaticFileService(config.raiz());
        HttpResponseWriter escritor = new HttpResponseWriter("Grupo-Redes-HTTP1.1", Clock.systemUTC());
        ExecutorService poolTrabalhadores = Executors.newFixedThreadPool(config.quantidadeTrabalhadores());

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("0.0.0.0", config.porta()));

            System.out.println("Servidor HTTP/1.1 escutando em 0.0.0.0:" + config.porta());
            System.out.println("Diretório raiz: " + config.raiz());
            System.out.println("Timeout ocioso de leitura: " + config.tempoLimiteOciosoMs() + " ms");
            System.out.println("Workers no pool: " + config.quantidadeTrabalhadores());

            while (true) {
                Socket cliente = serverSocket.accept();
                poolTrabalhadores.execute(new HttpConnectionHandler(
                        cliente, servicoArquivos, escritor, config.tempoLimiteOciosoMs()));
            }
        } finally {
            poolTrabalhadores.shutdownNow();
        }
    }
}
