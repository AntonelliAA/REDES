package br.edu.redes.http;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class HttpRequestReaderTest {
    private HttpRequestReaderTest() {
    }

    public static void main(String[] args) throws Exception {
        testarRequisicaoCompleta();
        testarLeituraFragmentada();
        testarMultiplasRequisicoesEmSequencia();
        testarInicioDaProximaRequisicaoNoMesmoBuffer();
        testarEofLimpo();
        testarEofPrematuro();
        testarErrosSintaxe();
        testarCabecalhosDeCorpo();
        testarConnectionRepetido();
        testarHost();
        testarTransferEncoding();
        testarListasRepetidas();
        testarAlvoAbsoluto();
        testarHeadEmErrosDeParsing();
        testarLimiteTamanho();
        testarTerminadorFragmentadoNoLimite();
        System.out.println("HttpRequestReaderTest: OK");
    }

    private static void testarRequisicaoCompleta() throws Exception {
        String texto = "GET /index.html HTTP/1.1\r\nHost: 127.0.0.1:8080\r\nUser-Agent: curl/8.0\r\n\r\n";
        HttpRequestReader leitor = new HttpRequestReader(new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1)), 8192);
        HttpRequest requisicao = leitor.ler();

        TestSupport.checar(requisicao != null, "A requisição não deve ser nula");
        TestSupport.checar("GET".equals(requisicao.metodo()), "Método deve ser GET");
        TestSupport.checar("/index.html".equals(requisicao.alvo()), "Alvo deve ser /index.html");
        TestSupport.checar("HTTP/1.1".equals(requisicao.versao()), "Versão deve ser HTTP/1.1");
        TestSupport.checar("127.0.0.1:8080".equals(requisicao.obterCabecalho("host")), "Cabeçalho host em minúsculas");
        TestSupport.checar("127.0.0.1:8080".equals(requisicao.obterCabecalho("HOST")), "Cabeçalho host case-insensitive");
    }

    private static void testarLeituraFragmentada() throws Exception {
        String texto = "GET /style.css HTTP/1.1\r\nHost: localhost\r\nAccept: text/css\r\n\r\n";
        InputStream streamLento = new FragmentedInputStream(new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1)), 2);
        HttpRequestReader leitor = new HttpRequestReader(streamLento, 8192);
        HttpRequest requisicao = leitor.ler();

        TestSupport.checar(requisicao != null, "Requisição lida em stream fragmentado não deve ser nula");
        TestSupport.checar("/style.css".equals(requisicao.alvo()), "Alvo da requisição fragmentada");
        TestSupport.checar("text/css".equals(requisicao.obterCabecalho("accept")), "Cabeçalho accept da requisição fragmentada");
    }

    private static void testarMultiplasRequisicoesEmSequencia() throws Exception {
        String texto = "GET /primeiro HTTP/1.1\r\nHost: teste\r\n\r\nGET /segundo HTTP/1.1\r\nHost: teste\r\n\r\n";
        HttpRequestReader leitor = new HttpRequestReader(new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1)), 8192);

        HttpRequest req1 = leitor.ler();
        TestSupport.checar(req1 != null && "/primeiro".equals(req1.alvo()), "Primeira requisição deve ser /primeiro");

        HttpRequest req2 = leitor.ler();
        TestSupport.checar(req2 != null && "/segundo".equals(req2.alvo()), "Segunda requisição deve ser /segundo");

        HttpRequest req3 = leitor.ler();
        TestSupport.checar(req3 == null, "Terceira leitura em EOF limpo deve retornar null");
    }

    private static void testarEofLimpo() throws Exception {
        HttpRequestReader leitor = new HttpRequestReader(new ByteArrayInputStream(new byte[0]), 8192);
        TestSupport.checar(leitor.ler() == null, "EOF imediato deve retornar null");
    }

    private static void testarInicioDaProximaRequisicaoNoMesmoBuffer() throws Exception {
        String primeira = "GET /primeiro HTTP/1.1\r\nHost: teste\r\n\r\n";
        String segunda = "HEAD /segundo HTTP/1.1\r\nHost: teste\r\n\r\n";
        InputStream entrada = new FragmentedInputStream(new ByteArrayInputStream(
                (primeira + segunda).getBytes(StandardCharsets.ISO_8859_1)), primeira.length() + 10);
        HttpRequestReader leitor = new HttpRequestReader(entrada, 8192);

        TestSupport.checar("/primeiro".equals(leitor.ler().alvo()), "Primeira requisição completa");
        HttpRequest proxima = leitor.ler();
        TestSupport.checar("HEAD".equals(proxima.metodo()) && "/segundo".equals(proxima.alvo()),
                "Início da próxima requisição deve ser preservado até chegarem os bytes restantes");
        TestSupport.checar(leitor.ler() == null, "Ambas as requisições devem ser consumidas");
    }

    private static void testarEofPrematuro() {
        String textoIncompleto = "GET /index.html HTTP/1.1\r\nHost: local";
        HttpRequestReader leitor = new HttpRequestReader(new ByteArrayInputStream(textoIncompleto.getBytes(StandardCharsets.ISO_8859_1)), 8192);
        TestSupport.esperarExcecao(BadRequestException.class, leitor::ler);
    }

    private static void testarErrosSintaxe() {
        // Quatro campos na request line
        TestSupport.esperarExcecao(BadRequestException.class, () -> {
            new HttpRequestReader(new ByteArrayInputStream("GET / a HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1)), 1024).ler();
        });

        // Dois campos na request line
        TestSupport.esperarExcecao(BadRequestException.class, () -> {
            new HttpRequestReader(new ByteArrayInputStream("GET /index.html\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1)), 1024).ler();
        });

        // Versão errada
        TestSupport.esperarExcecao(BadRequestException.class, () -> {
            new HttpRequestReader(new ByteArrayInputStream("GET / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1)), 1024).ler();
        });

        // Header sem dois pontos
        TestSupport.esperarExcecao(BadRequestException.class, () -> {
            new HttpRequestReader(new ByteArrayInputStream("GET / HTTP/1.1\r\nHeaderSemSeparador\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1)), 1024).ler();
        });

        // Header duplicado
        TestSupport.esperarExcecao(BadRequestException.class, () -> {
            new HttpRequestReader(new ByteArrayInputStream("GET / HTTP/1.1\r\nHost: a\r\nHost: b\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1)), 1024).ler();
        });

        String[] invalidas = {
            "GET / HTTP/1.1 \r\nHost: teste\r\n\r\n",
            "GET\t / HTTP/1.1\r\nHost: teste\r\n\r\n",
            "G(ET / HTTP/1.1\r\nHost: teste\r\n\r\n",
            "GET /arquivo\t HTTP/1.1\r\nHost: teste\r\n\r\n",
            "GET /arquivo\n HTTP/1.1\r\nHost: teste\r\n\r\n",
            "GET /arquivo#fragmento HTTP/1.1\r\nHost: teste\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: teste\r\nX-Teste : valor\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: teste\r\n X-Teste: valor\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: teste\r\nX(Test): valor\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: teste\nX-Teste: valor\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: teste\r\nX-Teste: \u0000valor\r\n\r\n",
            "GET / HTTP/1.1\r\n\r\n"
        };
        for (String texto : invalidas) {
            HttpRequestReader leitor = new HttpRequestReader(
                    new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1)), 1024);
            TestSupport.esperarExcecao(BadRequestException.class, leitor::ler);
        }
    }

    private static void testarCabecalhosDeCorpo() throws Exception {
        String[] invalidos = {
            "Content-Length: -1",
            "Content-Length: +1",
            "Content-Length: ",
            "Content-Length: 1a",
            "Content-Length: 1, 1",
            "Content-Length: 9223372036854775808",
            "Content-Length: 1\r\nContent-Length: 1",
            "Content-Length: 1\r\nTransfer-Encoding: chunked"
        };
        for (String cabecalho : invalidos) {
            String texto = "GET / HTTP/1.1\r\nHost: teste\r\n" + cabecalho + "\r\n\r\n";
            HttpRequestReader leitor = new HttpRequestReader(
                    new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1)), 1024);
            TestSupport.esperarExcecao(BadRequestException.class, leitor::ler);
        }

        String texto = "GET / HTTP/1.1\r\nHost: teste\r\nContent-Length:\t000 \t\r\n\r\n";
        HttpRequest requisicao = new HttpRequestReader(
                new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1)), 1024).ler();
        TestSupport.checar("000".equals(requisicao.obterCabecalho("content-length")),
                "Content-Length aceita dígitos e whitespace opcional ao redor do valor");
    }

    private static void testarConnectionRepetido() throws Exception {
        String texto = "GET / HTTP/1.1\r\nHost: teste\r\nConnection: keep-alive\r\nConnection: close\r\n\r\n";
        HttpRequest requisicao = new HttpRequestReader(
                new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1)), 1024).ler();
        TestSupport.checar("keep-alive, close".equals(requisicao.obterCabecalho("connection")),
                "Connection repetido preserva todos os tokens, incluindo close");
    }

    private static void testarHost() throws Exception {
        String[] validos = {"", "localhost", "127.0.0.1:8080", "rede.exemplo", "[::1]",
                "[2001:db8::1]:8080", "[::ffff:192.0.2.1]", "localhost:"};
        for (String host : validos) {
            HttpRequest requisicao = ler("GET / HTTP/1.1\r\nHost: " + host + "\r\n\r\n");
            TestSupport.checar(host.equals(requisicao.obterCabecalho("host")), "Host válido: " + host);
        }
        String[] invalidos = {"nome invalido", "localhost:abc", "localhost:-1", "user@host",
                "localhost/path", "localhost?query", "localhost#fragment", "::1", "[::1",
                "[abc]", "[1:2:3]:80", "[::1]resto", "[::1]:abc", "host%zz", "host\\outro"};
        for (String host : invalidos) {
            TestSupport.esperarExcecao(BadRequestException.class,
                    () -> ler("GET / HTTP/1.1\r\nHost: " + host + "\r\n\r\n"));
        }
    }

    private static void testarTransferEncoding() throws Exception {
        String[] validos = {"chunked", "Chunked", "gzip, chunked", "gzip\r\nTransfer-Encoding: chunked",
                "gzip,\tchunked"};
        for (String valor : validos) {
            TestSupport.checar(ler("GET / HTTP/1.1\r\nHost: teste\r\nTransfer-Encoding: "
                    + valor + "\r\n\r\n") != null, "Transfer-Encoding válido: " + valor);
        }
        String[] invalidos = {"", "gzip", "chunked, gzip", "chunked, chunked", "chunked; x=1",
                "chunked,", ",chunked", "gzip,,chunked", "gzip chunked", "gzip; x=1, chunked", "chun(ked",
                "chunked\r\nTransfer-Encoding: gzip"};
        for (String valor : invalidos) {
            TestSupport.esperarExcecao(BadRequestException.class,
                    () -> ler("GET / HTTP/1.1\r\nHost: teste\r\nTransfer-Encoding: " + valor + "\r\n\r\n"));
        }
    }

    private static void testarListasRepetidas() throws Exception {
        HttpRequest requisicao = ler("GET / HTTP/1.1\r\nHost: teste\r\n"
                + "Accept: text/plain\r\nAccept: text/html;q=0.5\r\n"
                + "Accept-Encoding: gzip\r\nAccept-Encoding: identity\r\n"
                + "Cache-Control: no-cache\r\nCache-Control: max-age=0\r\n\r\n");
        TestSupport.checar("text/plain, text/html;q=0.5".equals(requisicao.obterCabecalho("accept")),
                "Accept repetido deve preservar valores e ordem");
        TestSupport.checar("gzip, identity".equals(requisicao.obterCabecalho("accept-encoding")),
                "Accept-Encoding repetido é uma lista");
        TestSupport.checar("no-cache, max-age=0".equals(requisicao.obterCabecalho("cache-control")),
                "Cache-Control repetido é uma lista");
        TestSupport.esperarExcecao(BadRequestException.class,
                () -> ler("GET / HTTP/1.1\r\nHost: a\r\nHOST: a\r\n\r\n"));
        TestSupport.esperarExcecao(BadRequestException.class,
                () -> ler("GET / HTTP/1.1\r\nHost: a\r\nContent-Length: 0\r\ncontent-length: 0\r\n\r\n"));
        TestSupport.esperarExcecao(BadRequestException.class,
                () -> ler("GET / HTTP/1.1\r\nHost: a\r\nAuthorization: a\r\nAuthorization: b\r\n\r\n"));
    }

    private static void testarAlvoAbsoluto() throws Exception {
        String[][] validos = {
            {"http://localhost:8080/ok.txt", "/ok.txt"},
            {"http://localhost", "/"},
            {"HTTP://localhost?chave=valor", "/?chave=valor"},
            {"http://[::1]:8080/a%20b.txt?q=%23", "/a%20b.txt?q=%23"},
            {"http://localhost/../segredo", "/../segredo"},
            {"http://localhost/%2e%2e/%2e%2e/segredo", "/%2e%2e/%2e%2e/segredo"}
        };
        for (String metodo : new String[] {"GET", "HEAD"}) {
            for (String[] alvo : validos) {
                HttpRequest requisicao = ler(metodo + " " + alvo[0] + " HTTP/1.1\r\nHost: teste\r\n\r\n");
                TestSupport.checar(alvo[1].equals(requisicao.alvo()),
                        "Forma absoluta deve preservar o caminho bruto, inclusive travessia: " + alvo[0]);
            }
        }
        String[] invalidos = {"http://localhost:abc/ok.txt", "http:///ok.txt", "http://user@host/ok.txt",
                "http://localhost/ok.txt#fragmento", "http://[abc]/ok.txt", "http://localhost/%zz",
                "http:arquivo", "arquivo", "*"};
        for (String alvo : invalidos) {
            TestSupport.esperarExcecao(BadRequestException.class,
                    () -> ler("GET " + alvo + " HTTP/1.1\r\nHost: teste\r\n\r\n"));
        }
        TestSupport.checar("OPTIONS".equals(ler("OPTIONS * HTTP/1.1\r\nHost: teste\r\n\r\n").metodo()),
                "Método não suportado deve chegar ao handler para resposta 405");
    }

    private static void testarHeadEmErrosDeParsing() throws Exception {
        String[] falhasAposHead = {
            "HEAD / HTTP/1.1\r\nSemSeparador\r\n\r\n",
            "HEAD / HTTP/1.1\r\n\r\n",
            "HEAD / HTTP/1.1\r\nHost: nome invalido\r\n\r\n",
            "HEAD / HTTP/1.1\r\nHost: teste",
            "HEAD / HTTP/1.1\r\nHost: teste\r\nX-Long: " + "a".repeat(200) + "\r\n\r\n",
            "HEAD / HTTP/1.1\r\nHost: teste\r\nX-Long: " + "a".repeat(200)
        };
        for (String texto : falhasAposHead) {
            HttpRequestReader leitor = new HttpRequestReader(new FragmentedInputStream(
                    new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1)), 2), 100);
            checarErroHead(leitor, true);
        }
        String[] linhasInvalidas = {
            "HEAD / HTTP/1.0\r\nHost: teste\r\n\r\n",
            "HEAD  / HTTP/1.1\r\nHost: teste\r\n\r\n",
            "HEAD / HTTP/1.1",
            "HEAD /#fragmento HTTP/1.1\r\nHost: teste\r\n\r\n",
            "HEAD http://localhost:abc/ HTTP/1.1\r\nHost: teste\r\n\r\n",
            "GET / HTTP/1.1\r\nSemSeparador\r\n\r\nHEAD / HTTP/1.1\r\nHost: teste\r\n\r\n"
        };
        for (String texto : linhasInvalidas) {
            checarErroHead(new HttpRequestReader(new ByteArrayInputStream(
                    texto.getBytes(StandardCharsets.ISO_8859_1)), 1024), false);
        }
        String sequencia = "HEAD / HTTP/1.1\r\nHost: teste\r\n\r\nGET / HTTP/1.1\r\nSemSeparador\r\n\r\n";
        HttpRequestReader leitor = new HttpRequestReader(new ByteArrayInputStream(
                sequencia.getBytes(StandardCharsets.ISO_8859_1)), 1024);
        TestSupport.checar("HEAD".equals(leitor.ler().metodo()), "Primeiro HEAD válido");
        checarErroHead(leitor, false);
    }

    private static void checarErroHead(HttpRequestReader leitor, boolean esperado) throws Exception {
        try {
            leitor.ler();
            throw new AssertionError("Esperava BadRequestException");
        } catch (BadRequestException e) {
            TestSupport.checar(e.apenasCabecalhos() == esperado,
                    "HEAD só deve omitir corpo quando a linha desta requisição foi validada");
        }
    }

    private static HttpRequest ler(String texto) throws Exception {
        return new HttpRequestReader(new ByteArrayInputStream(texto.getBytes(StandardCharsets.ISO_8859_1)),
                8192).ler();
    }

    private static void testarLimiteTamanho() {
        // Cabeçalho maior que o limite configurado (limite de 100 bytes)
        String textoLongo = "GET / HTTP/1.1\r\nX-Long: " + "a".repeat(200) + "\r\n\r\n";
        HttpRequestReader leitor = new HttpRequestReader(new ByteArrayInputStream(textoLongo.getBytes(StandardCharsets.ISO_8859_1)), 100);
        TestSupport.esperarExcecao(BadRequestException.class, leitor::ler);
    }

    private static void testarTerminadorFragmentadoNoLimite() throws Exception {
        String cabecalho = "GET / HTTP/1.1\r\nHost: teste";
        byte[] bytes = (cabecalho + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1);
        for (int tamanhoFragmento = 1; tamanhoFragmento <= 4; tamanhoFragmento++) {
            HttpRequestReader leitor = new HttpRequestReader(
                    new FragmentedInputStream(new ByteArrayInputStream(bytes), tamanhoFragmento), cabecalho.length());
            TestSupport.checar(leitor.ler() != null,
                    "Cabeçalho no limite deve aceitar CRLFCRLF fragmentado");
            TestSupport.checar(leitor.ler() == null, "Terminador completo deve ser consumido");
        }
        HttpRequestReader leitor = new HttpRequestReader(new ByteArrayInputStream(bytes), cabecalho.length() - 1);
        TestSupport.esperarExcecao(BadRequestException.class, leitor::ler);
    }

    private static class FragmentedInputStream extends FilterInputStream {
        private final int chunkSize;

        protected FragmentedInputStream(InputStream in, int chunkSize) {
            super(in);
            this.chunkSize = chunkSize;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return super.read(b, off, Math.min(len, chunkSize));
        }
    }
}
