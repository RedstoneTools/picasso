package tools.redstone.picasso.adapter;

/**
 * Mono-directional adapter function.
 */
public interface AdapterFunction {

    Adapter<?, ?> adapter(); // The adapter providing this function
    Class<?> srcClass();     // The source class
    Class<?> dstClass();     // The destination class

    /**
     * Adapt the given value which is expected to be
     * assignable from srcClass to a type of dstClass.
     *
     * @param in The source value.
     * @return The destination value.
     */
    Object adapt(Object in);

}
