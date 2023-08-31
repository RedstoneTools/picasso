package tools.redstone.picasso.adapter;

import org.objectweb.asm.Type;
import tools.redstone.picasso.util.asm.ASMUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@SuppressWarnings({ "rawtypes" })
public interface AdapterRegistry {

    /**
     * Creates and registers an adapter for the given handle
     * and abstraction classes.
     *
     * @param handleClass The handle class.
     * @param abstractionClass The abstraction class.
     * @param <H> The handle type.
     * @param <A> The abstraction type.
     * @return The adapter.
     * @see Adapter#forHandleAbstraction(Class, Class)
     */
    default <H, A> Adapter<H, A> registerHandleAdapter(Class<H> handleClass, Class<A> abstractionClass) {
        Adapter<H, A> adapter = Adapter.forHandleAbstraction(handleClass, abstractionClass);
        register(adapter);
        return adapter;
    }

    /**
     * Register the given adapter to this registry.
     *
     * @param adapter The adapter.
     */
    void register(Adapter<?, ?> adapter);

    /**
     * Get the adapter for the given source and destination type.
     * Will return null if absent.
     *
     * @param aType Type A.
     * @param bType Type B.
     * @return The adapter if present.
     */
    default AdapterFunction findAdapterFunction(Type aType, Type bType) {
        Class<?> aCl = ASMUtil.asClass(aType);
        Class<?> bCl = ASMUtil.asClass(bType);
        return findAdapterFunction(aCl, bCl);
    }

    /**
     * Get the adapter for the given source and destination type.
     * Will return null if absent.
     *
     * @param aCl Class A.
     * @param bCl Class B.
     * @return The adapter if present.
     */
    AdapterFunction findAdapterFunction(Class<?> aCl, Class<?> bCl);

    /**
     * Create a lazy function which searches for the adapter
     * dynamically then caches the result.
     *
     * @param aType Type A.
     * @param bType Type B.
     * @return The function.
     */
    Function lazyAdaptingFunction(Class<?> aType, Class<?> bType);

}
