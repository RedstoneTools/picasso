package tools.redstone.picasso.usage;

/** Purely to denote that a class is an abstraction */
public interface Abstraction {

    /**
     * Method which signifies that the caller is not implemented.
     *
     * @throws NotImplementedException Always.
     */
    default <T> T unimplemented() {
        throw new NotImplementedException(null);
    }

    /**
     * Uses the appropriate adapter if present registered in
     * {@link tools.redstone.picasso.adapter.AdapterRegistry} to
     * convert the given value to a value of type T.
     *
     * This should never be called at runtime.
     * This directive should be replaced by the bytecode analyzer/transformer.
     *
     * @param val The value to transform.
     * @param <T> The output value type.
     * @throws UnsupportedOperationException If there is no adapter for val -> T
     * @return The mapped/adapted value.
     */
    default <T> T adapt(Object val) { throw new AssertionError(); }

}
