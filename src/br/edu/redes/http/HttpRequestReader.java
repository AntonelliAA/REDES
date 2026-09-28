package br.edu.redes.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HttpRequestReader {
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
        while (true) {
            int indiceFim = localizarFimDosCabecalhos();
            if (indiceFim >= 0) {
                int tamanhoCabecalho = indiceFim - inicio;
                if (tamanhoCabecalho > maxBytesCabecalho) {
                    throw new BadRequestException("Tamanho do cabeçalho (" + tamanhoCabecalho + " bytes) excedeu o limite permitido.");
                }

                byte[] bytesCabecalho = Arrays.copyOfRange(buffer, inicio, indiceFim);
                inicio = indiceFim + 4; // Pula os 4 bytes do \r\n\r\n

                return interpretarRequisicao(bytesCabecalho);
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

    private HttpRequest interpretarRequisicao(byte[] bytesCabecalho) throws BadRequestException {
        String texto = new String(bytesCabecalho, StandardCharsets.ISO_8859_1);
        String[] linhas = texto.split("\r\n", -1);

        if (linhas.length == 0 || linhas[0].isBlank()) {
            throw new BadRequestException("Requisição vazia ou linha de requisição ausente.");
        }

        String linhaRequisicao = linhas[0];
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

        Map<String, String> cabecalhos = new HashMap<>();
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
                if ("connection".equals(nomeCabecalho)) {
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

        return new HttpRequest(metodo, alvo, versao, cabecalhos);
    }

    private boolean tokenValido(String texto) {
        return texto.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    }
}
