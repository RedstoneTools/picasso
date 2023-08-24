package tools.redstone.picasso;

import tools.redstone.picasso.adapter.Adapter;
import tools.redstone.picasso.adapter.AdapterAnalysisHook;
import tools.redstone.picasso.analysis.ClassAnalysisHook;
import tools.redstone.picasso.usage.Abstraction;
import tools.redstone.picasso.util.PackageWalker;
import tools.redstone.picasso.adapter.AdapterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Provides Abstracraft services like the {@link AbstractionProvider} for the
 * rest of the Abstracraft project. This is so other modules can use local
 * {@link AbstractionProvider}s if they want.
 *
 * This is the only class which is actually specific to Abstracraft. The rest of
 * the core is reusable in other projects.
 */
public class Abstracraft {

    // Finds implementation classes as resources
    public interface ImplFinder {
        Stream<PackageWalker.Resource> findResources(AbstractionProvider manager);

        static ImplFinder inPackage(Class<?> codeSrc, String pkg) {
            var walker = new PackageWalker(codeSrc, pkg);
            return __ -> walker.findResources();
        }
    }

    private static final Abstracraft INSTANCE = new Abstracraft();

    public static Abstracraft getInstance() {
        return INSTANCE;
    }

    /**
     * The abstraction manager.
     */
    private final AbstractionManager manager = AbstractionManager.getInstance();

    /**
     * The abstraction provider.
     */
    private final AbstractionProvider provider = manager.createProvider()
            .addAnalysisHook(AbstractionProvider.excludeCallsOnSelfAsDependencies())
            .addAnalysisHook(AbstractionProvider.checkDependenciesForInterface(Abstraction.class, true))
            .addAnalysisHook(AbstractionProvider.checkStaticFieldsNotNull())
            .addAnalysisHook(AbstractionProvider.checkForExplicitImplementation(Abstraction.class))
            .addAnalysisHook(AbstractionProvider.autoRegisterLoadedImplClasses())
            .addAnalysisHook(new AdapterAnalysisHook(Abstraction.class, getAdapterRegistry()));

    // The packages to find implementation classes to
    // register from
    private final List<ImplFinder> implFinders = new ArrayList<>();

    {
        implFinders.add(ImplFinder.inPackage(Abstraction.class, ABSTRACRAFT_IMPLEMENTATION_PACKAGE));
    }

    protected AbstractionProvider getProvider() {
        return provider;
    }

    // The package under which all impls are located
    public static final String ABSTRACRAFT_IMPLEMENTATION_PACKAGE = "tools.redstone.abstracraft.impl";

    /**
     * Get the adapter registry used
     *
     * @return The adapter registry.
     */
    public AdapterRegistry getAdapterRegistry() {
        return AdapterRegistry.getInstance();
    }

    public void registerAdapter(Adapter<?, ?> adapter) {
        getAdapterRegistry().register(adapter);
    }

    /**
     * Adds the given analysis hook to the manager.
     *
     * @param hook The hook.
     */
    public void addAnalysisHook(ClassAnalysisHook hook) {
        provider.addAnalysisHook(hook);
    }

    /**
     * Adds the given implementation class finder.
     *
     * @param finder The finder.
     */
    public void addImplementationFinder(ImplFinder finder) {
        implFinders.add(finder);
    }

    /**
     * Registers all implementation classes found by the given
     * implementation finders.
     */
    public void findAndRegisterImplementations() {
        for (var finder : implFinders) {
            provider.registerImplsFromResources(finder.findResources(provider));
        }
    }

    /**
     * Get a class by the given name if loaded, otherwise
     * transform it using the abstraction manager.
     *
     * @param name The class name.
     * @return The class.
     */
    public Class<?> getOrTransformClass(String name) {
        return provider.findClass(name);
    }

    @SuppressWarnings("unchecked")
    public <A> Class<? extends A> getImplementationClass(Class<A> abstraction) {
        return (Class<? extends A>) manager.getImplByClass(abstraction);
    }

}
