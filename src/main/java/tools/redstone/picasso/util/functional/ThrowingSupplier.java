package tools.redstone.picasso.util.functional;

public interface ThrowingSupplier<T, E extends Throwable> {
    static <T> ThrowingSupplier<T, Throwable> throwsAll(ThrowingSupplier<T, Throwable> in) {
        return in;
    }

    static <T> T safe(ThrowingSupplier<T, Throwable> in) {
        return in.runSafe();
    }

    T get() throws E;

    default T runSafe() {
        try {
            return get();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
