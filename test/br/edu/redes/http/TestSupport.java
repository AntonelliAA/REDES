package br.edu.redes.http;

public final class TestSupport {
    @FunctionalInterface
    public interface ThrowingRunnable {
        void i_run() throws Throwable;
    }

    private TestSupport() {
    }

    public static void i_check(boolean condition_a, String message_a) {
        if (!condition_a) {
            throw new AssertionError(message_a);
        }
    }

    public static void i_expectThrows(Class<? extends Throwable> type_a, ThrowingRunnable action_a) {
        try {
            action_a.i_run();
        } catch (Throwable exception_a) {
            i_check(type_a.isInstance(exception_a), "esperava " + type_a.getName() + ", recebeu " + exception_a.getClass().getName());
            return;
        }
        throw new AssertionError("esperava exceção " + type_a.getName());
    }
}

