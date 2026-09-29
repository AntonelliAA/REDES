package br.edu.redes.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HttpRequestReader {
    private static final String TOKEN = "[!#$%&'*+.^_`|~0-9A-Za-z-]+";
    // Apenas campos cuja gramática permite uma lista podem ser unidos por vírgula.
    private static final Set<String> CAMPOS_EM_LISTA = Set.of(
            "accept", "accept-charset", "accept-encoding", "accept-language", "cache-control",
            "connection", "content-encoding", "content-language", "expect", "forwarded",
            "if-match", "if-none-match", "pragma", "te", "trailer", "transfer-encoding",
            "upgrade", "via");

    private final InputStream entrada;
    private final int maxBytesCabecalho;
    private byte[] buffer;
    private int inicio;
    private int fim;

    public HttpRequestReader(InputStream entrada, int maxBytesCabecalho) {
        if (entrada == null) {
            throw new IllegalArgumentException("O stream de entrada não pode ser nulo.");
        }
        if (maxBytesCabecalho <= 0) {
            throw new IllegalArgumentException("O limite de bytes do cabeçalho deve ser positivo.");
        }
        this.entrada = entrada;
        this.maxBytesCabecalho = maxBytesCabecalho;
        this.buffer = new byte[Math.min(4096, maxBytesCabecalho)];
        this.inicio = 0;
        this.fim = 0;
    }

    public HttpRequest ler() throws IOException, BadRequestException {
        LinhaRequisicao linhaRequisicao = null;
        try {
            while (true) {
                if (linhaRequisicao == null) {
                    int fimDaLinha = localizarFimDaLinha();
                    if (fimDaLinha >= 0) {
                        linhaRequisicao = interpretarLinhaRequisicao(new String(buffer, inicio,
                                fimDaLinha - inicio, StandardCharsets.ISO_8859_1));
                    }
                }
                int indiceFim = localizarFimDosCabecalhos();
                if (indiceFim >= 0) {
                    int tamanhoCabecalho = indiceFim - inicio;
                    if (tamanhoCabecalho > maxBytesCabecalho) {
                        throw new BadRequestException("Tamanho do cabeçalho (" + tamanhoCabecalho + " bytes) excedeu o limite permitido.");
                    }

                    byte[] bytesCabecalho = Arrays.copyOfRange(buffer, inicio, indiceFim);
                    inicio = indiceFim + 4; // Pula os 4 bytes do \r\n\r\n

                    return interpretarRequisicao(bytesCabecalho, linhaRequisicao);
                }

                // Os três primeiros bytes de CRLFCRLF podem chegar separados do último.
                if ((fim - inicio) - 3 > maxBytesCabecalho) {
                    throw new BadRequestException("Cabeçalhos excederam o limite máximo permitido sem encontrar o terminador CRLF.");
                }

                garantirEspacoNoBuffer();

                int bytesLidos = entrada.read(buffer, fim, buffer.length - fim);
                if (bytesLidos == -1) {
                    if (inicio == fim) {
                        return null; // Encerramento limpo da conexão pelo cliente
                    }
                    throw new BadRequestException("Conexão fechada prematuramente antes do término da requisição HTTP.");
                }

                fim += bytesLidos;
            }
        } catch (BadRequestException e) {
            // Só uma linha completa e válida permite identificar HEAD; nunca examine
            // bytes da próxima requisição para decidir se a resposta de erro tem corpo.
            boolean apenasCabecalhos = linhaRequisicao != null && "HEAD".equals(linhaRequisicao.metodo());
            throw new BadRequestException(e.getMessage(), e, apenasCabecalhos);
        }
    }

    private int localizarFimDaLinha() {
        for (int i = inicio; i < fim - 1; i++) {
            if (buffer[i] == '\r' && buffer[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private int localizarFimDosCabecalhos() {
        for (int i = inicio; i <= fim - 4; i++) {
            if (buffer[i] == '\r' && buffer[i + 1] == '\n' && buffer[i + 2] == '\r' && buffer[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private void garantirEspacoNoBuffer() {
        if (inicio > 0) {
            int tamanhoPendente = fim - inicio;
            System.arraycopy(buffer, inicio, buffer, 0, tamanhoPendente);
            inicio = 0;
            fim = tamanhoPendente;
        }

        if (fim == buffer.length) {
            int novaCapacidade = Math.min(buffer.length * 2, maxBytesCabecalho + 2048);
            if (novaCapacidade <= buffer.length) {
                novaCapacidade = buffer.length + 1024;
            }
            buffer = Arrays.copyOf(buffer, novaCapacidade);
        }
    }

    private LinhaRequisicao interpretarLinhaRequisicao(String linhaRequisicao) throws BadRequestException {
        if (linhaRequisicao.isBlank()) {
            throw new BadRequestException("Requisição vazia ou linha de requisição ausente.");
        }

        String[] partes = linhaRequisicao.split(" ", -1);
        if (partes.length != 3) {
            throw new BadRequestException("Linha de requisição inválida. Esperado: <MÉTODO> <ALVO> <VERSÃO>");
        }

        String metodo = partes[0];
        String alvo = partes[1];
        String versao = partes[2];

        if (metodo.isEmpty() || alvo.isEmpty() || versao.isEmpty()) {
            throw new BadRequestException("Campos vazios encontrados na linha de requisição.");
        }

        if (!tokenValido(metodo)) {
            throw new BadRequestException("Método contém caracteres inválidos.");
        }
        for (int i = 0; i < alvo.length(); i++) {
            char caractere = alvo.charAt(i);
            if (caractere <= 32 || caractere == 127 || caractere == '#') {
                throw new BadRequestException("Alvo contém espaços, caracteres de controle ou fragmento.");
            }
        }

        if (!"HTTP/1.1".equals(versao)) {
            throw new BadRequestException("Versão HTTP não suportada: " + versao + ". Esperado HTTP/1.1.");
        }

        if ("GET".equals(metodo) || "HEAD".equals(metodo)) {
            alvo = normalizarAlvo(alvo);
        }
        return new LinhaRequisicao(metodo, alvo, versao);
    }

    private HttpRequest interpretarRequisicao(byte[] bytesCabecalho, LinhaRequisicao requisicao)
            throws BadRequestException {
        String texto = new String(bytesCabecalho, StandardCharsets.ISO_8859_1);
        String[] linhas = texto.split("\r\n", -1);
        Map<String, String> cabecalhos = new LinkedHashMap<>();
        for (int i = 1; i < linhas.length; i++) {
            String linha = linhas[i];
            if (linha.isEmpty()) {
                continue;
            }

            int indiceDoisPontos = linha.indexOf(':');
            if (indiceDoisPontos <= 0) {
                throw new BadRequestException("Cabeçalho malformado, ausência de dois-pontos: " + linha);
            }

            String nomeCabecalho = linha.substring(0, indiceDoisPontos).toLowerCase(Locale.ROOT);
            String valorCabecalho = linha.substring(indiceDoisPontos + 1);

            if (!tokenValido(nomeCabecalho)) {
                throw new BadRequestException("Nome de cabeçalho inválido.");
            }
            for (int j = 0; j < valorCabecalho.length(); j++) {
                char caractere = valorCabecalho.charAt(j);
                if ((caractere < 32 && caractere != '\t') || caractere == 127) {
                    throw new BadRequestException("Valor de cabeçalho contém caracteres de controle.");
                }
            }
            valorCabecalho = valorCabecalho.trim();

            if (cabecalhos.containsKey(nomeCabecalho)) {
                if (CAMPOS_EM_LISTA.contains(nomeCabecalho)) {
                    cabecalhos.put(nomeCabecalho, cabecalhos.get(nomeCabecalho) + ", " + valorCabecalho);
                    continue;
                }
                throw new BadRequestException("Cabeçalho duplicado rejeitado: " + nomeCabecalho);
            }

            cabecalhos.put(nomeCabecalho, valorCabecalho);
        }

        if (!cabecalhos.containsKey("host")) {
            throw new BadRequestException("Cabeçalho Host obrigatório em HTTP/1.1.");
        }
        // Host vazio é permitido quando a URI de destino não define autoridade.
        if (!cabecalhos.get("host").isEmpty()) {
            validarAutoridade(cabecalhos.get("host"));
        }

        String tamanhoCorpo = cabecalhos.get("content-length");
        if (tamanhoCorpo != null) {
            if (!tamanhoCorpo.matches("[0-9]+") || cabecalhos.containsKey("transfer-encoding")) {
                throw new BadRequestException("Comprimento do corpo inválido ou ambíguo.");
            }
            try {
                Long.parseLong(tamanhoCorpo);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Comprimento do corpo excede o limite suportado.");
            }
        }

        String codificacao = cabecalhos.get("transfer-encoding");
        if (codificacao != null) {
            validarCodificacao(codificacao);
        }

        return new HttpRequest(requisicao.metodo(), requisicao.alvo(), requisicao.versao(), cabecalhos);
    }

    private String normalizarAlvo(String alvo) throws BadRequestException {
        if (alvo.startsWith("/")) {
            return alvo;
        }
        try {
            URI uri = new URI(alvo);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getRawAuthority() == null
                    || uri.getRawFragment() != null) {
                throw new BadRequestException("Alvo deve ser um caminho absoluto ou uma URI HTTP absoluta.");
            }
            validarAutoridade(uri.getRawAuthority());
            String caminho = uri.getRawPath();
            if (caminho == null || caminho.isEmpty()) {
                caminho = "/";
            }
            // Não normalize '..' nem decodifique percent-encoding: o serviço de
            // arquivos deve verificar a travessia no caminho original da URI.
            return caminho + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (URISyntaxException e) {
            throw new BadRequestException("URI absoluta inválida.", e);
        }
    }

    private void validarAutoridade(String autoridade) throws BadRequestException {
        try {
            // URI valida nome/IP e porta localmente, sem consultar DNS.
            URI uri = new URI("http://" + autoridade).parseServerAuthority();
            if (uri.getHost() == null || uri.getRawUserInfo() != null
                    || !uri.getRawPath().isEmpty() || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw new BadRequestException("Host deve conter apenas nome/IP e porta opcional.");
            }
        } catch (URISyntaxException e) {
            throw new BadRequestException("Host ou porta inválidos.", e);
        }
    }

    private void validarCodificacao(String valor) throws BadRequestException {
        // Este servidor aceita somente listas simples, sem parâmetros de codificação.
        String[] codificacoes = valor.split(",", -1);
        for (int i = 0; i < codificacoes.length; i++) {
            String codificacao = codificacoes[i].trim();
            if (!tokenValido(codificacao)) {
                throw new BadRequestException("Transfer-Encoding deve conter uma lista de tokens sem parâmetros.");
            }
            if ("chunked".equalsIgnoreCase(codificacao) && i != codificacoes.length - 1) {
                throw new BadRequestException("chunked deve aparecer somente no final de Transfer-Encoding.");
            }
        }
        if (!"chunked".equalsIgnoreCase(codificacoes[codificacoes.length - 1].trim())) {
            throw new BadRequestException("Transfer-Encoding em requisição deve terminar com chunked.");
        }
    }

    private boolean tokenValido(String texto) {
        return texto.matches(TOKEN);
    }

    private record LinhaRequisicao(String metodo, String alvo, String versao) {
    }
}
