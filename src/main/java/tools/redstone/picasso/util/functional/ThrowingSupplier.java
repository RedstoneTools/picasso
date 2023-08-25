package tools.redstone.picasso.util.functional;

public interface ThrowingSupplier<T, E extends Throwable> {

    /** Basically a shortcut for casting because that's ugly */
    static <T> ThrowingSupplier<T, Throwable> throwsAll(ThrowingSupplier<T, Throwable> in) {
        return in;
    }

    /**
     * Call this supplier, retrieving the value.
     *
     * @return The value.
     * @throws E Any exception which it may throw.
     */
    T get() throws E;

    // Run #get() caught
    default T caught() {
        try {
            return get();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

}
