package br.edu.redes.http;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StaticFileServiceTest {
    private StaticFileServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        Path pastaPai = Files.createTempDirectory("http-files-");
        Path pastaRaiz = Files.createDirectory(pastaPai.resolve("www"));
        Path arquivoExterno = pastaPai.resolve("outside.txt");
        Files.writeString(arquivoExterno, "segredo");

        Files.writeString(pastaRaiz.resolve("index.html"), "<h1>Olá Mundo</h1>");
        Files.writeString(pastaRaiz.resolve("space name.txt"), "conteúdo com espaço");
        Files.write(pastaRaiz.resolve("image.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        Files.write(pastaRaiz.resolve("unknown.bin"), new byte[]{1, 2, 3});

        Path subPasta = Files.createDirectory(pastaRaiz.resolve("safe"));
        Files.writeString(subPasta.resolve("doc.txt"), "dentro de safe");

        StaticFileService servico = new StaticFileService(pastaRaiz);

        // 1. Arquivos válidos e tipos MIME
        FileResult r1 = servico.obter("/index.html");
        TestSupport.checar(r1.status() == 200, "GET /index.html deve retornar 200");
        TestSupport.checar("text/html; charset=utf-8".equals(r1.tipoConteudo()), "MIME deve ser text/html");
        TestSupport.checar(new String(r1.corpo(), StandardCharsets.UTF_8).contains("Olá Mundo"), "Corpo do HTML");

        FileResult rEspaco = servico.obter("/space%20name.txt");
        TestSupport.checar(rEspaco.status() == 200, "GET /space%20name.txt deve retornar 200");
        TestSupport.checar("text/plain; charset=utf-8".equals(rEspaco.tipoConteudo()), "MIME deve ser text/plain");

        FileResult rPng = servico.obter("/image.png");
        TestSupport.checar(rPng.status() == 200, "GET /image.png deve retornar 200");
        TestSupport.checar("image/png".equals(rPng.tipoConteudo()), "MIME deve ser image/png");

        FileResult rBin = servico.obter("/unknown.bin");
        TestSupport.checar(rBin.status() == 200, "GET /unknown.bin deve retornar 200");
        TestSupport.checar("application/octet-stream".equals(rBin.tipoConteudo()), "MIME fallback deve ser octet-stream");

        String[][] tipos = {
                {"css", "text/css; charset=utf-8"},
                {"js", "text/javascript; charset=utf-8"},
                {"json", "application/json; charset=utf-8"},
                {"jpg", "image/jpeg"},
                {"pdf", "application/pdf"}
        };
        for (String[] tipo : tipos) {
            Files.writeString(pastaRaiz.resolve("arquivo." + tipo[0]), "teste");
            FileResult resultado = servico.obter("/arquivo." + tipo[0]);
            TestSupport.checar(resultado.status() == 200 && tipo[1].equals(resultado.tipoConteudo()),
                    "MIME obrigatório para ." + tipo[0]);
        }

        // 2. Resolução de diretório para index.html e query strings
        FileResult rRaiz = servico.obter("/");
        TestSupport.checar(rRaiz.status() == 200, "GET / deve servir index.html");

        FileResult rQuery = servico.obter("/index.html?parametro=123&outro=abc");
        TestSupport.checar(rQuery.status() == 200, "Query string deve ser ignorada na resolução de arquivo");

        // 3. Arquivo inexistente
        FileResult rNaoExiste = servico.obter("/inexistente.html");
        TestSupport.checar(rNaoExiste.status() == 404, "Arquivo inexistente deve retornar 404");

        // 4. Tentativas de Directory Traversal (devem retornar 403)
        FileResult rTrav1 = servico.obter("/../../outside.txt");
        TestSupport.checar(rTrav1.status() == 403, "Travessia /../../outside.txt deve retornar 403");

        FileResult rTrav2 = servico.obter("/%2e%2e/%2e%2e/outside.txt");
        TestSupport.checar(rTrav2.status() == 403, "Travessia com percent-encoding deve retornar 403");

        FileResult rTrav3 = servico.obter("/safe/%2e%2e/%2e%2e/outside.txt");
        TestSupport.checar(rTrav3.status() == 403, "Travessia a partir de subpasta deve retornar 403");

        // 5. Requisições malformadas (devem retornar 400)
        FileResult rHexInvalido = servico.obter("/arquivo%ZZ.txt");
        TestSupport.checar(rHexInvalido.status() == 400, "%ZZ deve retornar 400");

        FileResult rNul = servico.obter("/arquivo%00.txt");
        TestSupport.checar(rNul.status() == 400, "Byte NUL (%00) deve retornar 400");

        FileResult rSemBarra = servico.obter("index.html");
        TestSupport.checar(rSemBarra.status() == 400, "Alvo sem barra inicial deve retornar 400");

        TestSupport.checar(servico.obter("/arquivo%FF.txt").status() == 400, "UTF-8 inválido deve retornar 400");
        TestSupport.checar(servico.obter("/arquivo%2").status() == 400, "Percent-encoding incompleto deve retornar 400");
        TestSupport.checar(servico.obter("/arquivo\0.txt").status() == 400, "NUL literal deve retornar 400");
        Files.writeString(pastaRaiz.resolve("a+b.txt"), "mais");
        TestSupport.checar(servico.obter("/a+b.txt").status() == 200, "Sinal + no caminho não representa espaço");

        // Links simbólicos precisam ser verificados também ao escolher o index.html.
        testarLinksSimbolicos(servico, pastaRaiz, arquivoExterno);

        System.out.println("StaticFileServiceTest: OK");
    }

    private static void testarLinksSimbolicos(StaticFileService servico, Path raiz, Path externo) throws Exception {
        try {
            Files.createSymbolicLink(raiz.resolve("externo.txt"), externo);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
            System.out.println("Links simbólicos: teste indisponível neste sistema (" + e.getMessage() + ")");
            return;
        }
        TestSupport.checar(servico.obter("/externo.txt").status() == 403, "Link para arquivo externo deve retornar 403");

        Path pasta = Files.createDirectory(raiz.resolve("indice-externo"));
        Files.createSymbolicLink(pasta.resolve("index.html"), externo);
        TestSupport.checar(servico.obter("/indice-externo/").status() == 403,
                "Index simbólico não pode expor arquivo fora da raiz");

        Files.createSymbolicLink(raiz.resolve("diretorio-externo"), externo.getParent());
        TestSupport.checar(servico.obter("/diretorio-externo/outside.txt").status() == 403,
                "Link para diretório externo deve retornar 403");

        Path pastaInterna = Files.createDirectory(raiz.resolve("indice-interno"));
        Files.createSymbolicLink(pastaInterna.resolve("index.html"), raiz.resolve("index.html"));
        TestSupport.checar(servico.obter("/indice-interno/").status() == 200,
                "Index simbólico dentro da raiz continua permitido");
    }
}
