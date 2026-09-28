package br.edu.redes.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

public final class HttpResponseWriterTest {
    private HttpResponseWriterTest() {
    }

    public static void main(String[] args) throws Exception {
        Clock relogioFixo = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC);
        HttpResponseWriter escritor = new HttpResponseWriter("Grupo-Redes", relogioFixo);

        // 1. Teste de resposta GET 200 OK
        byte[] corpoHtml = "<h1>Sucesso</h1>".getBytes(StandardCharsets.UTF_8);
        HttpResponse respGet = new HttpResponse(200, "text/html; charset=utf-8", corpoHtml, false, Map.of());

        ByteArrayOutputStream baosGet = new ByteArrayOutputStream();
        escritor.escrever(baosGet, respGet, false);
        String textoGet = baosGet.toString(StandardCharsets.ISO_8859_1);

        TestSupport.checar(textoGet.startsWith("HTTP/1.1 200 OK\r\n"), "Linha de status 200 OK");
        TestSupport.checar(textoGet.contains("Date: Mon, 21 Sep 2026 12:00:00 GMT\r\n"), "Date em formato IMF-fixdate GMT");
        TestSupport.checar(textoGet.contains("Server: Grupo-Redes\r\n"), "Cabeçalho Server");
        TestSupport.checar(textoGet.contains("Content-Length: " + corpoHtml.length + "\r\n"), "Content-Length correto");
        TestSupport.checar(textoGet.contains("Content-Type: text/html; charset=utf-8\r\n"), "Content-Type correto");
        TestSupport.checar(!textoGet.contains("Connection: close"), "Sem Connection: close quando persistente");
        TestSupport.checar(textoGet.endsWith("<h1>Sucesso</h1>"), "Corpo presente no final de GET");

        // 2. Teste de resposta HEAD 200 OK
        ByteArrayOutputStream baosHead = new ByteArrayOutputStream();
        escritor.escrever(baosHead, respGet, true);
        String textoHead = baosHead.toString(StandardCharsets.ISO_8859_1);

        TestSupport.checar(textoHead.endsWith("\r\n\r\n"), "HEAD termina exatamente com \\r\\n\\r\\n sem corpo");
        TestSupport.checar(textoHead.contains("Content-Length: " + corpoHtml.length + "\r\n"), "HEAD mantém Content-Length do corpo");

        // 3. Teste de erro 405 Method Not Allowed com Allow: GET, HEAD e Connection: close
        HttpResponse resp405 = HttpResponse.erro(405, true, Map.of("Allow", "GET, HEAD"));
        ByteArrayOutputStream baos405 = new ByteArrayOutputStream();
        escritor.escrever(baos405, resp405, false);
        String texto405 = baos405.toString(StandardCharsets.ISO_8859_1);

        TestSupport.checar(texto405.startsWith("HTTP/1.1 405 Method Not Allowed\r\n"), "Status 405 Method Not Allowed");
        TestSupport.checar(texto405.contains("Allow: GET, HEAD\r\n"), "Cabeçalho Allow obrigatório");
        TestSupport.checar(texto405.contains("Connection: close\r\n"), "Connection: close presente");
        TestSupport.checar(texto405.contains("Content-Length: " + resp405.corpo().length + "\r\n"), "Content-Length de erro");

        System.out.println("HttpResponseWriterTest: OK");
    }
}
