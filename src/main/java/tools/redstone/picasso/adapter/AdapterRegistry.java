package tools.redstone.picasso.adapter;

import org.objectweb.asm.Type;
import tools.redstone.picasso.util.asm.ASMUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class AdapterRegistry {

    /**
     * Find the first adapter which maps the source class to any
     * destination type.
     *
     * @param srcClass The source type.
     * @param <S> The source value type.
     * @param <D> The destination value type.
     * @return The adapter or null if not found.
     */
    public static <S, D> Adapter<S, D> from(Class<S> srcClass) {
        return (Adapter<S, D>) INSTANCE.findMonoDirectionalFrom(srcClass);
    }

    /**
     * Find a registered adapter which maps the given source
     * type to the given destination type.
     *
     * @param srcClass The source type.
     * @param dstClass The destination type.
     * @param <S> The source value type.
     * @param <D> The destination value type.
     * @return The adapter or null if not found.
     */
    public static <S, D> Adapter<S, D> find(Class<S> srcClass, Class<D> dstClass) {
        return (Adapter<S, D>) INSTANCE.findMonoDirectional(srcClass, dstClass);
    }

    /**
     * Register the given adapter to this registry.
     *
     * @param adapter The adapter.
     */
    public static <A, B> Adapter<A, B> register(Adapter<A, B> adapter) {
        INSTANCE.put(adapter);
        return adapter;
    }

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
    public static <H, A> Adapter<H, A> registerHandleAdapter(Class<H> handleClass, Class<A> abstractionClass) {
        return register(Adapter.forHandleAbstraction(handleClass, abstractionClass));
    }

    private static final AdapterRegistry INSTANCE = new AdapterRegistry();
    public static AdapterRegistry getInstance() {
        return INSTANCE;
    }

    private final List<Adapter<?, ?>> adapters = new ArrayList<>(); // All registered bi-directional adapters

    /**
     * Register the given adapter to this registry.
     *
     * @param adapter The adapter.
     */
    public void put(Adapter<?, ?> adapter) {
        adapters.add(adapter);
    }

    /**
     * Get the first found adapter for the given source type.
     *
     * @param srcClass The source class.
     * @return The adapter if present.
     */
    public <S, D> Function<S, D> findMonoDirectionalFrom(Class<S> srcClass) {
        for (int i = 0, n = adapters.size(); i < n; i++) {
            Adapter<Object, Object> adapter = (Adapter<Object, Object>) adapters.get(i);

            // check types and reversed types
            if (srcClass == adapter.aClass()) {
                return s -> (D) adapter.toB(s);
            } else if (srcClass == adapter.bClass()) {
                return s -> (D) adapter.toA(s);
            }
        }

        return null;
    }

    /**
     * Get the adapter for the given source and destination type.
     * Will return null if absent.
     *
     * @param aType Type A.
     * @param bType Type B.
     * @return The adapter if present.
     */
    public Function findMonoDirectional(Type aType, Type bType) {
        Class<?> aCl = ASMUtil.asClass(aType);
        Class<?> bCl = ASMUtil.asClass(bType);
        return findMonoDirectional(aCl, bCl);
    }

    /**
     * Get the adapter for the given source and destination type.
     * Will return null if absent.
     *
     * @param aCl Class A.
     * @param bCl Class B.
     * @return The adapter if present.
     */
    public Function findMonoDirectional(Class<?> aCl, Class<?> bCl) {
        for (int i = 0, n = adapters.size(); i < n; i++) {
            Adapter<Object, Object> adapter = (Adapter<Object, Object>) adapters.get(i);

            // check types and reversed types
            if (aCl == adapter.aClass() && bCl == adapter.bClass()) {
                return adapter::toB;
            } else if (aCl == adapter.bClass() && bCl == adapter.aClass()) {
                return adapter::toA;
            }
        }

        return null;
    }

    /**
     * Create a lazy function which searches for the adapter
     * the first time it's actually used.
     *
     * @param aType Type A.
     * @param bType Type B.
     * @return The function.
     */
    public Function lazyRequireMonoDirectional(Type aType, Type bType) {
        return new Function<>() {
            Function cached;

            @Override
            public Object apply(Object o) {
                if (cached == null) {
                    cached = findMonoDirectional(aType, bType);

                    if (cached == null) {
                        throw new UnsupportedOperationException("No adapter for " + aType + " -> " + bType);
                    }
                }

                return cached.apply(o);
            }
        };
    }

}
