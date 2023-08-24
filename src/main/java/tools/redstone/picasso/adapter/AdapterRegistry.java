package tools.redstone.picasso.adapter;

import org.objectweb.asm.Type;
import tools.redstone.picasso.util.asm.ASMUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class AdapterRegistry {

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
    public void register(Adapter<?, ?> adapter) {
        adapters.add(adapter);
    }

    /**
     * Get the adapter for the given source and destination type.
     * Will return null if absent.
     *
     * Both type parameters have to be in JVM signature format
     * to retain generics data wherever possible.
     *
     * @param aType Type A.
     * @param bType Type B.
     * @return The adapter if present.
     */
    public Function findMonoDirectional(String aType, String bType) {
        // todo: maybe generics support idk
        Class<?> aCl = ASMUtil.asClass(Type.getType(aType));
        Class<?> bCl = ASMUtil.asClass(Type.getType(bType));

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

    public Function lazyRequireMonoDirectional(String aType, String bType) {
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
