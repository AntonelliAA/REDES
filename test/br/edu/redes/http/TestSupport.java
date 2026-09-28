package br.edu.redes.http;

public final class TestSupport {
    @FunctionalInterface
    public interface AcaoComExcecao {
        void executar() throws Throwable;
    }

    private TestSupport() {
    }

    public static void checar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }

    public static void esperarExcecao(Class<? extends Throwable> tipoEsperado, AcaoComExcecao acao) {
        try {
            acao.executar();
        } catch (Throwable e) {
            checar(tipoEsperado.isInstance(e), "Esperava " + tipoEsperado.getName() + ", mas recebeu " + e.getClass().getName());
            return;
        }
        throw new AssertionError("Esperava exceção " + tipoEsperado.getName());
    }
}
