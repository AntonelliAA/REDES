package br.edu.redes.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Map;

public class HttpConnectionHandler implements Runnable {
    private final Socket socket;
    private final StaticFileService arquivos;
    private final HttpResponseWriter escritor;
    private final int tempoLimiteOciosoMs;

    public HttpConnectionHandler(Socket socket, StaticFileService arquivos, HttpResponseWriter escritor, int tempoLimiteOciosoMs) {
        if (socket == null || arquivos == null || escritor == null) {
            throw new IllegalArgumentException("Socket, arquivos e escritor não podem ser nulos.");
        }
        if (tempoLimiteOciosoMs <= 0) {
            throw new IllegalArgumentException("O timeout de ociosidade deve ser positivo.");
        }
        this.socket = socket;
        this.arquivos = arquivos;
        this.escritor = escritor;
        this.tempoLimiteOciosoMs = tempoLimiteOciosoMs;
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(tempoLimiteOciosoMs);
            HttpRequestReader leitor = new HttpRequestReader(socket.getInputStream(), 65536);
            OutputStream saida = socket.getOutputStream();

            while (!socket.isClosed()) {
                HttpRequest requisicao;
                try {
                    requisicao = leitor.ler();
                } catch (BadRequestException e) {
                    HttpResponse resposta400 = HttpResponse.erro(400, true, Map.of());
                    escritor.escrever(saida, resposta400, e.apenasCabecalhos());
                    break;
                }

                if (requisicao == null) {
                    // Encerramento limpo pelo cliente (EOF)
                    break;
                }

                String cabecalhoConexao = requisicao.obterCabecalho("connection");
                boolean fecharConexao = false;
                if (cabecalhoConexao != null) {
                    for (String opcao : cabecalhoConexao.split(",")) {
                        if ("close".equalsIgnoreCase(opcao.trim())) {
                            fecharConexao = true;
                        }
                    }
                }

                // Este servidor só serve arquivos e não processa corpos de requisição.
                // Fecha para não interpretar esses bytes como a próxima requisição.
                String tamanhoCorpo = requisicao.obterCabecalho("content-length");
                if (requisicao.obterCabecalho("transfer-encoding") != null
                        || (tamanhoCorpo != null && Long.parseLong(tamanhoCorpo) > 0)) {
                    fecharConexao = true;
                }

                String metodo = requisicao.metodo();
                if (!"GET".equals(metodo) && !"HEAD".equals(metodo)) {
                    HttpResponse resposta405 = HttpResponse.erro(405, fecharConexao, Map.of("Allow", "GET, HEAD"));
                    escritor.escrever(saida, resposta405, false);
                } else {
                    boolean apenasCabecalhos = "HEAD".equals(metodo);
                    try (FileResult resultado = arquivos.obter(requisicao.alvo())) {
                        HttpResponse resposta = resultado.status() == 200
                                ? HttpResponse.deArquivo(resultado, fecharConexao)
                                : HttpResponse.erro(resultado.status(), fecharConexao, Map.of());
                        escritor.escrever(saida, resposta, apenasCabecalhos);
                    }
                }

                if (fecharConexao) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            // Timeout ocioso configurado expirado: fechar conexão silenciosamente
        } catch (IOException e) {
            // Falha de I/O ou conexão resetada pelo cliente
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
