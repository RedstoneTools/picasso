package tools.redstone.picasso;

import tools.redstone.picasso.usage.Abstraction;

/**
 * Represents an abstraction which wraps a handle,
 * already extends {@link Abstraction} so it can be
 * used directly by the abstraction tree.
 *
 * @param <H> The type of the handle.
 */
public abstract class HandleAbstraction<H> implements Abstraction {

    protected final H handle;

    public HandleAbstraction(H handle) {
        this.handle = handle;
    }

    // Can be overridden for extra flexibility.
    protected H getHandle0() {
        return handle;
    }

    /**
     * Get the handle cast to the given type.
     *
     * @param <T> The type to return.
     * @return The handle as the given type.
     */
    @SuppressWarnings("unchecked")
    public final <T extends H> T handle() {
        return (T) handle;
    }

}
