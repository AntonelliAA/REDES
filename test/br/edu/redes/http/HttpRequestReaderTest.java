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
