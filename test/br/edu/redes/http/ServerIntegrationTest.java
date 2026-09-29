package br.edu.redes.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ServerIntegrationTest {
    private ServerIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        Path pastaRaiz = Files.createTempDirectory("http-integration-root-");
        Files.writeString(pastaRaiz.resolve("index.html"), "<h1>Página Principal</h1>");
        Files.writeString(pastaRaiz.resolve("teste.txt"), "conteudo de teste");

        StaticFileService servicoArquivos = new StaticFileService(pastaRaiz);
        HttpResponseWriter escritor = new HttpResponseWriter("Grupo-Redes-Test", Clock.systemUTC());

        ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        int porta = serverSocket.getLocalPort();
        ExecutorService pool = Executors.newFixedThreadPool(4);

        Thread acceptThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket cliente = serverSocket.accept();
                    pool.submit(new HttpConnectionHandler(cliente, servicoArquivos, escritor, 1000));
                } catch (IOException ignored) {
                    break;
                }
            }
        });
        acceptThread.setDaemon(true);
        acceptThread.start();

        try {
            // 1. GET 200 OK
            testarGet200(porta);

            // 2. HEAD de sucesso e de erro, sem corpo antes da próxima resposta
            testarHead200(porta);
            testarHead404(porta);
            testarHeadMalformado(porta);
            testarNovosCasosDeParser(porta);

            // 3. POST 405 Method Not Allowed
            testarMetodoNaoPermitido405(porta);

            // 4. Request line inválida 400
            testarRequisicaoInvalida400(porta);

            // 5. Arquivo inexistente 404
            testarArquivoNaoEncontrado404(porta);

            // 6. Travessia de diretório 403
            testarTravessiaDiretorio403(porta);

            // 7. Dez requisições sequenciais no mesmo socket
            testarConexaoPersistente(porta);
            testarContentLengthZeroPersistente(porta);

            // 8. Pipelining seguro (2 requisições enviadas juntas)
            testarPipelining(porta);

            // 9. Encerramento com Connection: close
            testarConnectionClose(porta);
            testarTimeoutOcioso(porta);
            testarCorpoNaoViraRequisicao(porta, "POST", 405, "Content-Length");
            testarCorpoNaoViraRequisicao(porta, "GET", 200, "Content-Length");
            testarCorpoNaoViraRequisicao(porta, "POST", 405, "Transfer-Encoding");

            // 10. Concorrência não bloqueante (cliente lento não bloqueia cliente rápido)
            testarConcorrenciaNaoBloqueante(porta);

            System.out.println("ServerIntegrationTest: OK");
        } finally {
            serverSocket.close();
            pool.shutdownNow();
        }
    }

    private static void testarGet200(int porta) throws Exception {
        try (Socket socket = conectar(porta)) {
            enviarRequisicao(socket, "GET /teste.txt HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RespostaHttp resposta = lerResposta(socket);
            TestSupport.checar(resposta.status == 200, "GET deve retornar 200");
            TestSupport.checar("conteudo de teste".equals(new String(resposta.corpo, StandardCharsets.UTF_8)), "Corpo de GET correto");
            TestSupport.checar("text/plain; charset=utf-8".equals(resposta.obterCabecalho("content-type")), "Content-Type correto");
        }
    }

    private static void testarHeadMalformado(int porta) throws Exception {
        String[] cabecalhos = {
            "Host: localhost\r\nSemDoisPontos\r\n",
            "Host: nome invalido\r\n",
            "Host: localhost\r\nTransfer-Encoding: gzip\r\n",
            ""
        };
        for (String cabecalho : cabecalhos) {
            try (Socket socket = conectar(porta)) {
                enviarRequisicao(socket, "HEAD /teste.txt HTTP/1.1\r\n" + cabecalho + "\r\n");
                RespostaHttp resposta = lerResposta(socket, true);
                TestSupport.checar(resposta.status == 400, "HEAD malformado deve receber 400");
                TestSupport.checar("17".equals(resposta.obterCabecalho("content-length")),
                        "HEAD 400 mantém o tamanho que o GET teria");
                checarFechamento(socket, resposta);
            }
        }
    }

    private static void testarNovosCasosDeParser(int porta) throws Exception {
        String[][] casos = {
            {"GET /teste.txt HTTP/1.1\r\nHost: nome invalido\r\n", "400"},
            {"GET /teste.txt HTTP/1.1\r\nHost: localhost:abc\r\n", "400"},
            {"GET /teste.txt HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: gzip\r\n", "400"},
            {"GET /teste.txt HTTP/1.1\r\nHost: localhost\r\nAccept: text/plain\r\nAccept: text/html\r\n", "200"},
            {"GET http://localhost/space%20name.txt HTTP/1.1\r\nHost: localhost\r\n", "404"},
            {"GET http://localhost/teste.txt HTTP/1.1\r\nHost: localhost\r\n", "200"},
            {"GET http://localhost/%2e%2e/outside.txt HTTP/1.1\r\nHost: localhost\r\n", "403"}
        };
        for (String[] caso : casos) {
            try (Socket socket = conectar(porta)) {
                enviarRequisicao(socket, caso[0] + "Connection: close\r\n\r\n");
                RespostaHttp resposta = lerResposta(socket);
                TestSupport.checar(resposta.status == Integer.parseInt(caso[1]),
                        "Status esperado para " + caso[0]);
                checarFechamento(socket, resposta);
            }
        }
    }

    private static void testarHead200(int porta) throws Exception {
        try (Socket socket = conectar(porta)) {
            enviarRequisicao(socket, "HEAD /teste.txt HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RespostaHttp resposta = lerResposta(socket, true);
            TestSupport.checar(resposta.status == 200, "HEAD deve retornar 200");
            TestSupport.checar("17".equals(resposta.obterCabecalho("content-length")), "Content-Length reflete tamanho do corpo em HEAD");

            enviarRequisicao(socket, "GET /teste.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            RespostaHttp get = lerResposta(socket);
            TestSupport.checar(get.status == 200, "GET após HEAD deve ser uma resposta HTTP completa");
            TestSupport.checar("conteudo de teste".equals(new String(get.corpo, StandardCharsets.UTF_8)), "HEAD não pode deixar corpo antes da próxima resposta");
            TestSupport.checar(resposta.obterCabecalho("content-type").equals(get.obterCabecalho("content-type")), "HEAD e GET têm o mesmo Content-Type");
        }
    }

    private static void testarHead404(int porta) throws Exception {
        try (Socket socket = conectar(porta)) {
            enviarRequisicao(socket, "HEAD /naoexiste.html HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RespostaHttp head = lerResposta(socket, true);
            TestSupport.checar(head.status == 404, "HEAD de arquivo inexistente deve retornar 404");

            enviarRequisicao(socket, "GET /naoexiste.html HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            RespostaHttp get = lerResposta(socket);
            TestSupport.checar(get.status == 404, "GET após HEAD 404 não pode receber corpo residual");
            TestSupport.checar(head.obterCabecalho("content-length").equals(get.obterCabecalho("content-length")), "HEAD 404 preserva Content-Length do GET correspondente");
            TestSupport.checar(head.obterCabecalho("content-type").equals(get.obterCabecalho("content-type")), "HEAD 404 preserva Content-Type do GET correspondente");
            TestSupport.checar("404 Not Found\r\n".equals(new String(get.corpo, StandardCharsets.UTF_8)), "Corpo do GET 404 deve estar íntegro");
        }
    }

    private static void testarMetodoNaoPermitido405(int porta) throws Exception {
        try (Socket socket = conectar(porta)) {
            enviarRequisicao(socket, "POST /teste.txt HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RespostaHttp resposta = lerResposta(socket);
            TestSupport.checar(resposta.status == 405, "POST deve retornar 405");
            TestSupport.checar("GET, HEAD".equals(resposta.obterCabecalho("allow")), "Header Allow deve conter GET, HEAD");
        }
    }

    private static void testarRequisicaoInvalida400(int porta) throws Exception {
        try (Socket socket = conectar(porta)) {
            enviarRequisicao(socket, "GET / a b c HTTP/1.1\r\n\r\n");
            RespostaHttp resposta = lerResposta(socket);
            TestSupport.checar(resposta.status == 400, "Request line inválida deve retornar 400");
        }
    }

    private static void testarArquivoNaoEncontrado404(int porta) throws Exception {
        try (Socket socket = conectar(porta)) {
            enviarRequisicao(socket, "GET /naoexiste.html HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RespostaHttp resposta = lerResposta(socket);
            TestSupport.checar(resposta.status == 404, "Arquivo inexistente deve retornar 404");
        }
    }

    private static void testarTravessiaDiretorio403(int porta) throws Exception {
        try (Socket socket = conectar(porta)) {
            enviarRequisicao(socket, "GET /../../etc/passwd HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RespostaHttp resposta = lerResposta(socket);
            TestSupport.checar(resposta.status == 403, "Travessia de diretório deve retornar 403");
        }
    }

    private static void testarConexaoPersistente(int porta) throws Exception {
        try (Socket socket = conectar(porta)) {
            for (int i = 1; i <= 10; i++) {
                enviarRequisicao(socket, "GET /teste.txt HTTP/1.1\r\nHost: localhost\r\n\r\n");
                RespostaHttp resposta = lerResposta(socket);
                TestSupport.checar(resposta.status == 200, "Requisição persistente " + i + " deve retornar 200");
                TestSupport.checar("conteudo de teste".equals(new String(resposta.corpo, StandardCharsets.UTF_8)), "Corpo íntegro na requisição persistente " + i);
            }
        }
    }

    private static void testarContentLengthZeroPersistente(int porta) throws Exception {
        try (Socket socket = conectar(porta)) {
            enviarRequisicao(socket, "GET /teste.txt HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\n\r\n");
            RespostaHttp primeira = lerResposta(socket);
            TestSupport.checar(primeira.status == 200, "Content-Length zero deve ser aceito");
            TestSupport.checar(!"close".equalsIgnoreCase(primeira.obterCabecalho("connection")), "Content-Length zero não exige fechamento");

            enviarRequisicao(socket, "GET /teste.txt HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            TestSupport.checar(lerResposta(socket).status == 200, "Conexão continua utilizável após Content-Length zero");
        }
    }

    private static void testarPipelining(int porta) throws Exception {
        try (Socket socket = conectar(porta)) {
            String duploGet = "GET /teste.txt HTTP/1.1\r\nHost: localhost\r\n\r\nGET /index.html HTTP/1.1\r\nHost: localhost\r\n\r\n";
            enviarRequisicao(socket, duploGet);

            RespostaHttp r1 = lerResposta(socket);
            TestSupport.checar(r1.status == 200, "1ª resposta de pipelining");

            RespostaHttp r2 = lerResposta(socket);
            TestSupport.checar(r2.status == 200, "2ª resposta de pipelining");
        }
    }

    private static void testarConnectionClose(int porta) throws Exception {
        String[] cabecalhos = {
                "Connection: close\r\n",
                "Connection: keep-alive, CLOSE\r\n",
                "Connection: keep-alive\r\nConnection: close\r\n"
        };
        for (String cabecalho : cabecalhos) {
            try (Socket socket = conectar(porta)) {
                enviarRequisicao(socket, "GET /teste.txt HTTP/1.1\r\nHost: localhost\r\n" + cabecalho + "\r\n");
                RespostaHttp resposta = lerResposta(socket);
                TestSupport.checar(resposta.status == 200, "Status deve ser 200 com " + cabecalho);
                checarFechamento(socket, resposta);
            }
        }
    }

    private static void testarTimeoutOcioso(int porta) throws Exception {
        try (Socket socket = conectar(porta)) {
            enviarRequisicao(socket, "GET /teste.txt HTTP/1.1\r\nHost: localhost\r\n\r\n");
            TestSupport.checar(lerResposta(socket).status == 200, "GET antes do timeout deve retornar 200");

            socket.setSoTimeout(250);
            TestSupport.esperarExcecao(SocketTimeoutException.class, () -> socket.getInputStream().read());
            socket.setSoTimeout(3000);
            TestSupport.checar(socket.getInputStream().read() == -1, "Conexão ociosa deve fechar após o timeout do servidor");
        }
    }

    private static void testarCorpoNaoViraRequisicao(int porta, String metodo, int status, String tipoEnquadramento) throws Exception {
        String requisicaoDentroDoCorpo = "GET /index.html HTTP/1.1\r\nHost: localhost\r\n\r\n";
        String cabecalho;
        String corpo;
        if ("Content-Length".equals(tipoEnquadramento)) {
            cabecalho = "Content-Length: " + requisicaoDentroDoCorpo.length() + "\r\n";
            corpo = requisicaoDentroDoCorpo;
        } else {
            cabecalho = "Transfer-Encoding: chunked\r\n";
            corpo = Integer.toHexString(requisicaoDentroDoCorpo.length()) + "\r\n" + requisicaoDentroDoCorpo + "\r\n0\r\n\r\n";
        }

        try (Socket socket = conectar(porta)) {
            enviarRequisicao(socket, metodo + " /teste.txt HTTP/1.1\r\nHost: localhost\r\n" + cabecalho + "\r\n" + corpo);
            RespostaHttp resposta = lerResposta(socket);
            TestSupport.checar(resposta.status == status, metodo + " com corpo deve retornar " + status);
            if (status == 405) {
                TestSupport.checar("GET, HEAD".equals(resposta.obterCabecalho("allow")), "405 com corpo mantém Allow");
            }
            checarFechamento(socket, resposta);
        }
    }

    private static void checarFechamento(Socket socket, RespostaHttp resposta) throws IOException {
        TestSupport.checar("close".equalsIgnoreCase(resposta.obterCabecalho("connection")), "Resposta deve conter Connection: close");
        socket.setSoTimeout(500);
        TestSupport.checar(socket.getInputStream().read() == -1, "Servidor deve fechar sem enviar uma segunda resposta");
    }

    private static void testarConcorrenciaNaoBloqueante(int porta) throws Exception {
        CountDownLatch conexaoIniciada = new CountDownLatch(1);
        CountDownLatch clienteRapidoConcluiu = new CountDownLatch(1);
        CountDownLatch clienteLentoConcluiu = new CountDownLatch(1);

        // Cliente A (lento): conecta e envia apenas uma parte dos cabeçalhos
        Thread clienteLento = new Thread(() -> {
            try (Socket socketLento = conectar(porta)) {
                OutputStream out = socketLento.getOutputStream();
                out.write("GET /teste.txt HTTP/1.1\r\nHost: local".getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
                conexaoIniciada.countDown();

                // Aguarda o cliente B terminar com sucesso
                TestSupport.checar(clienteRapidoConcluiu.await(2, TimeUnit.SECONDS), "Cliente rápido deve concluir primeiro");
                enviarRequisicao(socketLento, "host\r\nConnection: close\r\n\r\n");
                TestSupport.checar(lerResposta(socketLento).status == 200, "Cliente lento também deve receber 200 ao completar a requisição");
                clienteLentoConcluiu.countDown();
            } catch (Exception ignored) {
            }
        });
        clienteLento.start();

        TestSupport.checar(conexaoIniciada.await(1, TimeUnit.SECONDS), "Cliente lento deve conectar antes do rápido");

        // Cliente B (rápido): conecta enquanto o cliente A está pendente e deve responder imediatamente
        long inicio = System.currentTimeMillis();
        try (Socket socketRapido = conectar(porta)) {
            enviarRequisicao(socketRapido, "GET /index.html HTTP/1.1\r\nHost: localhost\r\n\r\n");
            RespostaHttp resposta = lerResposta(socketRapido);
            long duracao = System.currentTimeMillis() - inicio;

            TestSupport.checar(resposta.status == 200, "Cliente rápido deve receber 200");
            TestSupport.checar(duracao < 1000, "Cliente rápido não pode ser bloqueado pelo cliente lento");
            clienteRapidoConcluiu.countDown();
        }

        clienteLento.join(2000);
        TestSupport.checar(clienteLentoConcluiu.getCount() == 0, "Ambos os clientes concorrentes devem ser atendidos");
    }

    private static Socket conectar(int porta) throws IOException {
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), porta);
        socket.setSoTimeout(3000);
        return socket;
    }

    private static void enviarRequisicao(Socket socket, String requisicao) throws IOException {
        OutputStream saida = socket.getOutputStream();
        saida.write(requisicao.getBytes(StandardCharsets.ISO_8859_1));
        saida.flush();
    }

    private static RespostaHttp lerResposta(Socket socket) throws IOException {
        return lerResposta(socket, false);
    }

    private static RespostaHttp lerResposta(Socket socket, boolean headOnly) throws IOException {
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream bufferCabecalho = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            bufferCabecalho.write(b);
            byte[] bytes = bufferCabecalho.toByteArray();
            if (bytes.length >= 4 && bytes[bytes.length - 4] == '\r' && bytes[bytes.length - 3] == '\n'
                    && bytes[bytes.length - 2] == '\r' && bytes[bytes.length - 1] == '\n') {
                break;
            }
        }

        String textoCabecalho = bufferCabecalho.toString(StandardCharsets.ISO_8859_1);
        TestSupport.checar(textoCabecalho.endsWith("\r\n\r\n"), "Resposta deve conter cabeçalhos completos");
        String[] linhas = textoCabecalho.split("\r\n");
        String linhaStatus = linhas[0];
        TestSupport.checar(linhaStatus.startsWith("HTTP/1.1 "), "Resposta deve começar pela linha HTTP, sem corpo residual de HEAD");
        int status = Integer.parseInt(linhaStatus.split(" ")[1]);

        java.util.Map<String, String> cabecalhos = new java.util.HashMap<>();
        for (int i = 1; i < linhas.length; i++) {
            if (linhas[i].isEmpty()) break;
            int idx = linhas[i].indexOf(':');
            if (idx > 0) {
                cabecalhos.put(linhas[i].substring(0, idx).trim().toLowerCase(java.util.Locale.ROOT),
                        linhas[i].substring(idx + 1).trim());
            }
        }

        byte[] corpo = new byte[0];
        if (!headOnly) {
            String lenStr = cabecalhos.get("content-length");
            if (lenStr != null) {
                int len = Integer.parseInt(lenStr);
                corpo = in.readNBytes(len);
                TestSupport.checar(corpo.length == len, "Corpo deve ter exatamente o Content-Length informado");
            }
        }

        return new RespostaHttp(status, cabecalhos, corpo);
    }

    private record RespostaHttp(int status, java.util.Map<String, String> cabecalhos, byte[] corpo) {
        public String obterCabecalho(String nome) {
            return cabecalhos.get(nome.toLowerCase(java.util.Locale.ROOT));
        }
    }
}
