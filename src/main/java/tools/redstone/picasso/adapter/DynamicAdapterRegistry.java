package tools.redstone.picasso.adapter;

import org.objectweb.asm.Type;
import tools.redstone.picasso.util.ReflectUtil;
import tools.redstone.picasso.util.asm.ASMUtil;
import tools.redstone.picasso.util.data.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * An adapter registry which provides dynamic/virtual
 * adapters, meaning an adapter is retrieved based on the
 * type of the instance provided, instead of a predetermined type.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class DynamicAdapterRegistry implements AdapterRegistry {

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
    public <H, A> Adapter<H, A> registerHandleAdapter(Class<H> handleClass, Class<A> abstractionClass) {
        Adapter<H, A> adapter = Adapter.forHandleAbstraction(handleClass, abstractionClass);
        register(adapter);
        return adapter;
    }

    private final Map<Pair<Class<?>, Class<?>>, AdapterFunction> cachedFunctions = new HashMap<>(); // The cache of found adapter functions
    private final List<Adapter<?, ?>> adapters = new ArrayList<>();                                 // All registered bi-directional adapters

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
     * @param aType Type A.
     * @param bType Type B.
     * @return The adapter if present.
     */
    public AdapterFunction findAdapterFunction(Type aType, Type bType) {
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
    @Override
    public AdapterFunction findAdapterFunction(Class<?> aCl, Class<?> bCl) {
        final Pair<Class<?>, Class<?>> pair = new Pair<>(aCl, bCl);

        // check cache
        AdapterFunction func = cachedFunctions.get(pair);
        if (func != null)
            return func;

        func = findAdapterFunction0(aCl, bCl);
        if (func != null) {
            cachedFunctions.put(pair, func);
        }

        return func;
    }

    private AdapterFunction findAdapterFunction0(Class<?> srcClass, Class<?> dstClass) {
        // todo: find a better algorithm
        //  like anything should be faster than this
        //  absolute dogshit algorithm but its cached
        //  so hopefully its not to slow

        Function bestFit = null;                   // The best function to adapt from src -> dst
        Adapter<?, ?> bestFitAdapter = null;       // The adapter for the best fit function
        Class<?> bestFitSrc = null;                // The source type for the best fit function
        Class<?> bestFitDst = null;                // The dest type for the best fit function
        int bestFitSeparation = Integer.MAX_VALUE; // The separation from srcClass -> bestFitSrcClass

        for (int i = 0, n = adapters.size(); i < n; i++) {
            Adapter<Object, Object> adapter = (Adapter<Object, Object>) adapters.get(i);
            Class<?> classA = adapter.aClass();
            Class<?> classB = adapter.bClass();

            // check for [A, B] = [ ? super SRC -> ? extends DST ]
            if (classA.isAssignableFrom(srcClass) &&
                dstClass.isAssignableFrom(classB)) {
                int separation = ReflectUtil.findSuperclassSeparation(classA, srcClass);
                if (separation < bestFitSeparation) {
                    bestFit = adapter::toB;
                    bestFitSeparation = separation;
                    bestFitAdapter = adapter;
                    bestFitSrc = classA;
                    bestFitDst = classB;
                }
            }

            // check for [A, B] = [ ? extends DST <- ? super SRC ]
            if (classB.isAssignableFrom(srcClass) &&
                dstClass.isAssignableFrom(classA)) {
                int separation = ReflectUtil.findSuperclassSeparation(classB, srcClass);
                if (separation < bestFitSeparation) {
                    bestFit = adapter::toA;
                    bestFitSeparation = separation;
                    bestFitAdapter = adapter;
                    bestFitSrc = classB;
                    bestFitDst = classA;
                }
            }
        }

        // create AdapterFunction from best fit function
        Function finalBestFit = bestFit;
        Adapter<?, ?> finalBestFitAdapter = bestFitAdapter;
        Class<?> finalBestFitSrc = bestFitSrc;
        Class<?> finalBestFitDst = bestFitDst;
        return bestFit == null ? null : new AdapterFunction() {
            @Override
            public Adapter<?, ?> adapter() {
                return finalBestFitAdapter;
            }

            @Override
            public Class<?> srcClass() {
                return finalBestFitSrc;
            }

            @Override
            public Class<?> dstClass() {
                return finalBestFitDst;
            }

            @Override
            public Object adapt(Object in) {
                return finalBestFit.apply(in);
            }
        };
    }

    @Override
    public Function lazyAdaptingFunction(Class<?> aClass, Class<?> bClass) {
        return o -> {
            if (o == null)
                return null; // cant adapt null

            return findAdapterFunction(o.getClass(), bClass).adapt(o);
        };
    }

}
