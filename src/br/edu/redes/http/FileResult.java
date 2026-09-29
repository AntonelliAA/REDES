package br.edu.redes.http;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

/** O resultado mantém o arquivo aberto até o fim da resposta. */
public record FileResult(int status, SeekableByteChannel canal, long tamanho, String tipoConteudo)
        implements AutoCloseable {
    public static FileResult erro(int status) {
        return new FileResult(status, null, 0, null);
    }

    @Override
    public void close() throws IOException {
        if (canal != null) {
            canal.close();
        }
    }
}
