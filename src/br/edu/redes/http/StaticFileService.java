package br.edu.redes.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

public class StaticFileService {
    private final Path raiz;
    private final Path raizReal;

    public StaticFileService(Path raiz) {
        if (raiz == null) {
            throw new IllegalArgumentException("O diretório raiz não pode ser nulo.");
        }
        try {
            this.raiz = raiz.toAbsolutePath().normalize();
            this.raizReal = this.raiz.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Diretório raiz inacessível: " + raiz, e);
        }
        if (!Files.isDirectory(this.raizReal)) {
            throw new IllegalArgumentException("O caminho configurado não é um diretório: " + raiz);
        }
    }

    public FileResult obter(String alvoRequisicao) {
        if (alvoRequisicao == null || alvoRequisicao.isEmpty()) {
            return new FileResult(400, null, null);
        }

        // Remove a query string (?...)
        String caminhoComBarra = alvoRequisicao;
        int indiceQuery = caminhoComBarra.indexOf('?');
        if (indiceQuery >= 0) {
            caminhoComBarra = caminhoComBarra.substring(0, indiceQuery);
        }

        if (!caminhoComBarra.startsWith("/")) {
            return new FileResult(400, null, null);
        }

        // Decodificação segura de percent-encoding
        String caminhoDecodificado;
        try {
            caminhoDecodificado = decodificarPercentEncoding(caminhoComBarra);
        } catch (IllegalArgumentException e) {
            return new FileResult(400, null, null);
        }

        // Remove barras iniciais redundantes
        while (caminhoDecodificado.startsWith("/")) {
            caminhoDecodificado = caminhoDecodificado.substring(1);
        }

        Path candidato;
        try {
            candidato = raiz.resolve(caminhoDecodificado).normalize();
        } catch (InvalidPathException e) {
            return new FileResult(400, null, null);
        }

        // Verificação 1: o caminho normalizado está estritamente contido no diretório raiz?
        if (!candidato.startsWith(raiz)) {
            return new FileResult(403, null, null);
        }

        // Se o arquivo ou diretório não existir fisicamente:
        if (!Files.exists(candidato)) {
            return new FileResult(404, null, null);
        }

        // Verificação 2: resolução segura de links simbólicos (symlinks)
        Path candidatoReal;
        try {
            candidatoReal = candidato.toRealPath();
        } catch (IOException e) {
            return new FileResult(404, null, null);
        }

        if (!candidatoReal.startsWith(raizReal)) {
            return new FileResult(403, null, null);
        }

        // Se for um diretório, tentar servir o index.html contido nele
        if (Files.isDirectory(candidatoReal)) {
            try {
                candidatoReal = candidatoReal.resolve("index.html").toRealPath();
            } catch (IOException e) {
                return new FileResult(404, null, null); // Nunca listar diretórios
            }
            if (!candidatoReal.startsWith(raizReal)) {
                return new FileResult(403, null, null);
            }
        }

        if (!Files.isRegularFile(candidatoReal)) {
            return new FileResult(404, null, null);
        }

        try {
            byte[] corpo = Files.readAllBytes(candidatoReal);
            String tipoMime = descobrirTipoMime(candidatoReal.getFileName().toString());
            return new FileResult(200, corpo, tipoMime);
        } catch (IOException e) {
            return new FileResult(404, null, null);
        }
    }

    private static String decodificarPercentEncoding(String entrada) {
        ByteArrayOutputStream bufferSaida = new ByteArrayOutputStream();
        int tamanho = entrada.length();

        for (int i = 0; i < tamanho; i++) {
            char c = entrada.charAt(i);
            if (c == '%') {
                if (i + 2 >= tamanho) {
                    throw new IllegalArgumentException("Sequência percent-encoding incompleta.");
                }
                char hex1 = entrada.charAt(i + 1);
                char hex2 = entrada.charAt(i + 2);
                int digito1 = Character.digit(hex1, 16);
                int digito2 = Character.digit(hex2, 16);
                if (digito1 < 0 || digito2 < 0) {
                    throw new IllegalArgumentException("Dígitos hexadecimais inválidos após %.");
                }
                int byteDecodificado = (digito1 << 4) + digito2;
                if (byteDecodificado == 0) {
                    throw new IllegalArgumentException("Byte NUL (0x00) proibido.");
                }
                bufferSaida.write(byteDecodificado);
                i += 2;
            } else {
                byte[] bytesChar = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bufferSaida.write(bytesChar, 0, bytesChar.length);
            }
        }

        CharsetDecoder decodificador = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            CharBuffer resultado = decodificador.decode(ByteBuffer.wrap(bufferSaida.toByteArray()));
            return resultado.toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("Sequência UTF-8 inválida no percent-decoding.", e);
        }
    }

    private static String descobrirTipoMime(String nomeArquivo) {
        String nomeMinusculo = nomeArquivo.toLowerCase(Locale.ROOT);
        int indicePonto = nomeMinusculo.lastIndexOf('.');
        if (indicePonto < 0) {
            return "application/octet-stream";
        }

        String extensao = nomeMinusculo.substring(indicePonto);
        return switch (extensao) {
            case ".html", ".htm" -> "text/html; charset=utf-8";
            case ".css" -> "text/css; charset=utf-8";
            case ".js" -> "text/javascript; charset=utf-8";
            case ".json" -> "application/json; charset=utf-8";
            case ".txt" -> "text/plain; charset=utf-8";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}
