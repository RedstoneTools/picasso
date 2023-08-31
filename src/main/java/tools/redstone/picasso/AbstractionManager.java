package tools.redstone.picasso;

import tools.redstone.picasso.adapter.AdapterAnalysisHook;
import tools.redstone.picasso.adapter.AdapterRegistry;
import tools.redstone.picasso.usage.Abstraction;

import java.util.*;

/**
 * Manages abstractions and their implementations.
 *
 * This is a singleton and is therefore shared across the runtime,
 * unlike the {@link AbstractionProvider} which can be instantiated
 * for specific use cases.
 *
 * @author orbyfied
 */
public class AbstractionManager {

    private static final AbstractionManager INSTANCE = new AbstractionManager();

    public static AbstractionManager getInstance() {
        return INSTANCE;
    }

    final Map<Class<?>, Class<?>> implByBaseClass = new HashMap<>(); // The registered implementation classes by base class
    final Set<Class<?>> registeredImplClasses = new HashSet<>();     // Set of all classes registerImpl() was called with


    /**
     * Get the base abstraction class from the given interface.
     *
     * @param startClass The starting interface.
     * @return The base abstraction class/interface.
     */
    public List<Class<?>> getApplicableAbstractionClasses(Class<?> startClass) {
        Class<?> current = startClass;
        outer: while (current != null) {
            if (current.isInterface())
                break;

            // find next interface by finding the
            // interface implementing Abstraction
            for (Class<?> itf : current.getInterfaces()) {
                if (Abstraction.class.isAssignableFrom(itf)) {
                    current = itf;
                    continue outer;
                }
            }

            // not an abstraction class, be lenient and return
            // an empty list
            return List.of();
        }

        if (current == startClass)
            // an interface, but not an abstraction class,
            // be lenient and return an empty list
            return List.of();

        if (current == null)
            throw new IllegalArgumentException("Could not find base abstraction for " + startClass);
        return List.of(current);
    }

    /**
     * Registers the given implementation class.
     *
     * Note that impl classes can be overwritten by different {@link AbstractionProvider}s.
     *
     * @param implClass The implementation.
     */
    public void registerImpl(Class<?> implClass) {
        if (registeredImplClasses.contains(implClass))
            return;

        var abstractionsImplemented = getApplicableAbstractionClasses(implClass);
        for (Class<?> kl : abstractionsImplemented) {
            implByBaseClass.put(kl, implClass);
        }

        registeredImplClasses.add(implClass);
    }

    /**
     * Get the implementation of the given class if present.
     *
     * @param baseClass The class.
     * @return The implementation.
     */
    public Class<?> getImplByClass(Class<?> baseClass) {
        return implByBaseClass.get(baseClass);
    }

    /**
     * Creates a new abstraction provider.
     *
     * @return The provider.
     */
    public AbstractionProvider createProvider() {
        return new AbstractionProvider(this);
    }

    public AbstractionProvider createDefaultProvider(AdapterRegistry adapterRegistry) {
        return new AbstractionProvider(this)
                .addAnalysisHook(AbstractionProvider.excludeCallsOnSelfAsDependencies())
                .addAnalysisHook(AbstractionProvider.checkDependenciesForInterface(Abstraction.class, true))
                .addAnalysisHook(AbstractionProvider.checkStaticFieldsNotNull())
                .addAnalysisHook(AbstractionProvider.checkForExplicitImplementation(Abstraction.class))
                .addAnalysisHook(AbstractionProvider.autoRegisterLoadedImplClasses())
                .addAnalysisHook(new AdapterAnalysisHook(Abstraction.class, adapterRegistry));
    }

}
