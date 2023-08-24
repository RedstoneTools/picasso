package tools.redstone.picasso;

import org.objectweb.asm.*;
import tools.redstone.picasso.analysis.*;
import tools.redstone.picasso.util.asm.ASMUtil;
import tools.redstone.picasso.util.PackageWalker;
import tools.redstone.picasso.util.ReflectUtil;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Provides systems, like dependency analysis and class
 * transformation, for abstractions.
 *
 * @author orbyfied
 */
public class AbstractionProvider {

    record DefaultImplAnalysis(Set<ReferenceInfo> unimplementedMethods) { }

    Predicate<String> classAuditPredicate = s -> true;                                          // The predicate for abstraction class names.
    Predicate<ReferenceAnalysis> requiredMethodPredicate = m -> m.optionalReferenceNumber <= 0; // The predicate for required methods.
    final List<ClassAnalysisHook> analysisHooks = new ArrayList<>();                            // The global dependency analysis hooks
    
    final AbstractionManager abstractionManager;                                                // The manager of abstractions and their impls
    final Map<ReferenceInfo, Boolean> implementedCache = new HashMap<>();                       // A cache to store whether a specific method is implemented for fast access

    final Map<ReferenceInfo, ReferenceAnalysis> refAnalysisMap = new HashMap<>();               // All analyzed methods by their descriptor
    final Map<String, ClassDependencyAnalyzer> analyzerMap = new HashMap<>();                   // All analyzers by class name
    final ClassLoader transformingClassLoader;

    final ClassDependencyAnalyzer partialAnalyzer;                                              // Class analyzer used to initiate partial analysis

    public AbstractionProvider(AbstractionManager manager) {
        this.abstractionManager = manager;

        // create class loader
        this.transformingClassLoader = ReflectUtil.transformingClassLoader(
                // name predicate
                this::shouldTransformClass,
                // parent class loader
                getClass().getClassLoader(),
                // transformer
                ((name, reader, writer) -> {
//                    System.out.println("LOADER DEBUG loading class(" + name + ")");
                    long t1 = System.currentTimeMillis();

                    var analyzer = analyzer(name, true);
                    if (analyzer.getClassAnalysis() == null || !analyzer.getClassAnalysis().completed)
                        analyzer.analyzeAndTransform();
                    analyzer.getClassNode().accept(writer);

                    long t2 = System.currentTimeMillis();
//                    System.out.println("LOADER DEBUG transforming class took " + (t2 - t1) + "ms");
                }), ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, false,
                klass -> {
                    // call class load hooks
                    for (var hook : analysisHooks) {
                        hook.onClassLoad(this, klass);
                    }
                });

        this.partialAnalyzer = new ClassDependencyAnalyzer(this, null);
    }

    public AbstractionProvider setClassAuditPredicate(Predicate<String> classAuditPredicate) {
        this.classAuditPredicate = classAuditPredicate;
        return this;
    }

    public AbstractionProvider setRequiredMethodPredicate(Predicate<ReferenceAnalysis> requiredMethodPredicate) {
        this.requiredMethodPredicate = requiredMethodPredicate;
        return this;
    }

    public Predicate<String> getClassAuditPredicate() {
        return classAuditPredicate;
    }

    public Predicate<ReferenceAnalysis> getRequiredMethodPredicate() {
        return requiredMethodPredicate;
    }

    public AbstractionManager abstractionManager() {
        return abstractionManager;
    }

    /**
     * Get the implementation class for the given abstraction class.
     *
     * @param baseClass The abstraction base class.
     * @return The impl class.
     */
    public Class<?> getImplByClass(Class<?> baseClass) {
        return abstractionManager.getImplByClass(baseClass);
    }

    /**
     * Loads and registers the class by the given name.
     *
     * @param implClassName The class name.
     */
    public void loadAndRegisterImpl(String implClassName) {
        final Class<?> klass = findClass(implClassName);
        abstractionManager.registerImpl(klass);
    }
    
    // Check whether the given ref is implemented
    // without referencing the cache
    private boolean isImplemented0(ReferenceInfo ref) {
        try {
            // check hooks
            for (var hook : analysisHooks) {
                var res = hook.checkImplemented(this, ref);
                if (res == null) continue;
                return res;
            }

            // otherwise just assume it is available
            // because it exists
            return true;
        } catch (Throwable t) {
            throw new RuntimeException("Error while checking implementation status of " + ref, t);
        }
    }

    /**
     * Check whether the given method is implemented for it's
     * owning abstraction.
     *
     * @param method The method.
     * @return Whether it is implemented.
     */
    public boolean isImplemented(ReferenceInfo method) {
        if (method.equals(ReferenceInfo.unimplemented()))
            return false;

        Boolean b = implementedCache.get(method);
        if (b != null)
            return b;

        implementedCache.put(method, b = isImplemented0(method));
        return b;
    }

    /**
     * Check whether all given methods are implemented.
     *
     * @param methods The methods.
     * @return Whether they are all implemented.
     */
    public boolean areAllImplemented(List<ReferenceInfo> methods) {
        if (methods == null)
            return true;

        for (ReferenceInfo i : methods) {
            if (!isImplemented(i)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check whether all the required given dependencies are implemented.
     *
     * @param dependencies The dependencies.
     * @return Whether they are all implemented.
     */
    public boolean areAllRequiredImplemented(List<ReferenceDependency> dependencies) {
        return areAllImplemented(dependencies.stream()
                .filter(d -> !d.optional())
                .map(ReferenceDependency::info)
                .toList());
    }

    /**
     * Manually set whether something is implemented.
     *
     * @param info The method.
     * @param b The status.
     */
    public void setImplemented(ReferenceInfo info, boolean b) {
        implementedCache.put(info, b);
    }

    /**
     * Get or create an analyzer for the given class name.
     *
     * @param className The class name.
     * @return The analyzer.
     */
    public ClassDependencyAnalyzer analyzer(String className, boolean ignoreLoadedClasses) {
        // get cached/active
        String publicName = className.replace('/', '.');
        ClassDependencyAnalyzer analyzer = analyzerMap.get(publicName);
        if (analyzer != null)
            return analyzer;

        try {
            className = className.replace('.', '/');

            if (ignoreLoadedClasses && ReflectUtil.findLoadedClassInParents(transformingClassLoader, publicName) != null) {
                return null;
            }

            if (!shouldTransformClass(publicName)) {
                return null;
            }

            // open resource
            String classAsPath = className + ".class";
            try (InputStream stream = transformingClassLoader.getResourceAsStream(classAsPath)) {
                if (stream == null)
                    throw new IllegalArgumentException("Could not find resource stream for " + classAsPath);
                byte[] bytes = stream.readAllBytes();

                ClassReader reader = new ClassReader(bytes);

                // create and register analyzer
                analyzer = new ClassDependencyAnalyzer(this, reader);
                analyzerMap.put(publicName, analyzer);

                analyzer.hooks.addAll(this.analysisHooks);

                return analyzer;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while creating MethodDependencyAnalyzer for class " + className, e);
        }
    }

    public ClassDependencyAnalyzer analyzer(Class<?> klass) {
        return analyzer(klass.getName(), false);
    }

    public ClassDependencyAnalyzer analyzerOrNull(String name) {
        return analyzerMap.get(name.replace('/', '.'));
    }

    public ClassDependencyAnalyzer analyzerOrNull(Class<?> klass) {
        return analyzerOrNull(klass.getName());
    }

    public ReferenceAnalysis getReferenceAnalysis(ReferenceInfo info) {
        return refAnalysisMap.get(info);
    }

    public ReferenceAnalysis registerAnalysis(ReferenceAnalysis analysis) {
        refAnalysisMap.put(analysis.ref, analysis);
        return analysis;
    }

    /**
     * Find/load a class using the transforming class loader
     * of this abstraction manager.
     *
     * @param name The name.
     * @return The class.
     */
    public Class<?> findClass(String name) {
        return ReflectUtil.getClass(name, this.transformingClassLoader);
    }

    /**
     * Analyzes and transforms the given method if it is not
     * being currently analyzed (recursion, it is present in the stack)
     *
     * @param context The context of the analysis.
     * @param info The method to analyze.
     * @return The analysis or null.
     */
    public ReferenceAnalysis publicReference(AnalysisContext context, ReferenceInfo info) {
        // check for cached
        var analysis = getReferenceAnalysis(info);
        if (analysis != null && analysis.complete && (!analysis.partial || info.isField()))
            return analysis;

        // fields are always partial
        if (info.isField()) {
            analysis = new ReferenceAnalysis(partialAnalyzer, info);
            analysis.partial = true;
            analysis.complete = true;
            refAnalysisMap.put(info, analysis);
            return analysis;
        }

        // analyze through owner class
        ClassDependencyAnalyzer analyzer = this.analyzer(info.internalClassName(), true);
        if (analyzer == null) {
            if (analysis != null)
                return analysis;
            return analysis = makePartial(info);
        } else if (analysis != null && analysis.partial) {
            // use actual analyzer to replace partial analysis
            var newAnalysis = analyzer.localMethod(context, info);
            if (newAnalysis.complete) {
                newAnalysis.refHooks.addAll(analysis.refHooks);
                newAnalysis.optionalReferenceNumber += analysis.optionalReferenceNumber;
            }

            return newAnalysis;
        }

        return analyzer.localMethod(context, info);
    }

    public ReferenceAnalysis makePartial(ReferenceInfo info) {
        var analysis = new ReferenceAnalysis(partialAnalyzer, info);
        analysis.partial = true;
        analysis.complete = true;
        refAnalysisMap.put(info, analysis);
        return analysis;
    }

    public boolean allImplemented(Class<?> klass) {
        var analyzer = analyzer(klass);
        if (analyzer == null || !analyzer.getClassAnalysis().completed)
            return false;
        return analyzer.getClassAnalysis().areAllImplemented(this);
    }

    /**
     * Get the class analysis for the given class, or null
     * if not available.
     *
     * @param klass The class.
     * @return The analysis.
     */
    public ClassDependencyAnalyzer.ClassAnalysis getClassAnalysis(Class<?> klass) {
        var analyzer = analyzer(klass);
        if (analyzer == null || !analyzer.getClassAnalysis().completed)
            return null;
        return analyzer.getClassAnalysis();
    }

    /**
     * Register the given analysis hook to this abstraction
     * manager and the partial analyzer.
     *
     * @param hook The hook.
     * @return This.
     */
    public AbstractionProvider addAnalysisHook(ClassAnalysisHook hook) {
        this.analysisHooks.add(hook);
        this.partialAnalyzer.addHook(hook);
        hook.onRegister(this);
        return this;
    }

    /**
     * Iterate over all given resources and try to load
     * and register them as impl classes.
     *
     * @param stream The resources.
     */
    public void registerImplsFromResources(Stream<PackageWalker.Resource> stream) {
        stream.forEach(resource -> loadAndRegisterImpl(resource.publicPath()));
    }

    /**
     * Check whether a class by the given name should be transformed
     * by this abstraction manager.
     *
     * @param name The name.
     * @return Whether it should be transformed.
     */
    public boolean shouldTransformClass(String name) {
        return !name.startsWith("java") && classAuditPredicate.test(name);
    }

    /* ------------ Hooks -------------- */

    public record ClassInheritanceChecker(Class<?> itf, String itfInternalName, Map<String, Boolean> cache) {
        // Class inheritance checkers by class checked
        private static final Map<Class<?>, ClassInheritanceChecker> checkerCache = new HashMap<>();

        public static ClassInheritanceChecker forClass(Class<?> itf) {
            return checkerCache.computeIfAbsent(itf, __ -> new ClassInheritanceChecker(itf, itf.getName().replace('.', '/'), new HashMap<>()));
        }

        /**
         * Checks whether the class/interface by the given name inherits
         * from this checker's class/interface.
         *
         * @param mgr The manager to resolve analyzers.
         * @param name The name of the class.
         * @return Whether the class inherits it.
         */
        public boolean checkClassInherits(AbstractionProvider mgr, String name) {
            Boolean b = cache.get(name);
            if (b != null)
                return b;

            try {
                // check for loaded class
                Class<?> klass = ReflectUtil.findLoadedClass(mgr.transformingClassLoader, name);
                if (klass != null) {
                    cache.put(name, b = itf.isAssignableFrom(klass));
                    return b;
                }

                // check for transform predicate, if false just
                // load the class using the manager class loader.
                // also, if were not currently transforming the class
                // we can safely transform it now without repercussions
                if (!mgr.shouldTransformClass(name) || mgr.analyzerOrNull(name) == null) {
                    cache.put(name, b = itf.isAssignableFrom(mgr.findClass(name)));
                    return b;
                }

                // analyze class node from analyzer
                // to find inheritance (shit)
                var analyzer = mgr.analyzerOrNull(name);
                var classNode = analyzer.getClassNode();

                b = checkClassInherits(mgr, classNode.superName.replace('/', '.'));
                int i = 0, n = classNode.interfaces.size();
                while (!b && i < n) { // try to find in interfaces
                    b = checkClassInherits(mgr, classNode.interfaces.get(i++).replace('/', '.'));
                }

                cache.put(name, b);
                return b;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** Stops the given methods from being included as dependencies */
    public static ClassAnalysisHook excludeNamesAsDependencies(final String... names) {
        final Set<String> nameSet = Set.of(names);
        return new ClassAnalysisHook() {
            @Override
            public Boolean isDependencyCandidate(AnalysisContext context, ReferenceInfo ref) {
                return nameSet.contains(ref.name()) ? false : null;
            }
        };
    }

    /** Excludes calls on methods of the calling class as dependencies */
    public static ClassAnalysisHook excludeCallsOnSelfAsDependencies() {
        return new ClassAnalysisHook() {
            @Override
            public Boolean isDependencyCandidate(AnalysisContext context, ReferenceInfo ref) {
                return ref.className().equals(context.currentMethod().className()) ? false : null;
            }
        };
    }

    /** Allows dependencies which call to a class implementing the given interface */
    public static ClassAnalysisHook checkDependenciesForInterface(final Class<?> itf, boolean includeFields) {
        final ClassInheritanceChecker checker = ClassInheritanceChecker.forClass(itf);
        return new ClassAnalysisHook() {
            @Override
            public Boolean isDependencyCandidate(AnalysisContext context, ReferenceInfo ref) {
                if (!includeFields && ref.isField())
                    return null;
                if (!checker.checkClassInherits(context.abstractionProvider(), ref.className()))
                    return false;
                return true;
            }
        };
    }

    /** Checks the bytecode and declaration class of methods to determine whether they are implemented */
    public static ClassAnalysisHook checkForExplicitImplementation(Class<?> unimplementedOwnerItf) {
        final ClassInheritanceChecker checker = ClassInheritanceChecker.forClass(unimplementedOwnerItf);
        return new ClassAnalysisHook() {
            final Map<Class<?>, DefaultImplAnalysis> defaultImplAnalysisCache = new HashMap<>(); // Cache for default implementation analysis per class

            // Check the bytecode of the owner of the given method
            // to see whether
            private boolean checkBytecodeImplemented(AbstractionProvider provider, Method method) {
                ReferenceInfo methodInfo = ReferenceInfo.forMethod(method);
                Class<?> klass = method.getDeclaringClass();

                // check cache
                DefaultImplAnalysis analysis = defaultImplAnalysisCache.get(klass);
                if (analysis != null)
                    return !analysis.unimplementedMethods().contains(methodInfo);

                // analyze bytecode
                final Set<ReferenceInfo> unimplementedMethods = new HashSet<>();
                ReflectUtil.analyze(klass, new ClassVisitor(ASMUtil.ASM_V) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        final ReferenceInfo currentMethod = ReferenceInfo.forMethodInfo(klass.getName(), name, descriptor, Modifier.isStatic(access));
                        return new MethodVisitor(ASMUtil.ASM_V) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                // check for Abstraction#unimplemented call
                                if (checker.checkClassInherits(provider, owner.replace('/', '.')) && "unimplemented".equals(name) && descriptor.startsWith("()")) {
                                    unimplementedMethods.add(currentMethod);
                                }
                            }
                        };
                    }
                });

                defaultImplAnalysisCache.put(klass, analysis = new DefaultImplAnalysis(unimplementedMethods));
                return !unimplementedMethods.contains(methodInfo);
            }

            @Override
            public Boolean checkImplemented(AbstractionProvider provider, ReferenceInfo ref) throws Throwable {
                if (ref.isField())
                    return null; // nothing to say
                var refClass = ReflectUtil.getClass(ref.className());

                // get implementation class for abstraction
                Class<?> implClass = provider.abstractionManager().getImplByClass(refClass);
                if (implClass == null)
                    // object not implemented at all
                    return false;

                // check ref declaration
                Method m = implClass.getMethod(ref.name(), ASMUtil.asClasses(ref.type().getArgumentTypes()));

                if (m.getDeclaringClass() == refClass)
                    return checkBytecodeImplemented(provider, m);
                if (m.getDeclaringClass().isInterface() && !m.isDefault())
                    return false;
                return !Modifier.isAbstract(m.getModifiers());
            }
        };
    }

    /** Checks static field dependencies for a not null value to determine if they're implemented */
    public static ClassAnalysisHook checkStaticFieldsNotNull() {
        return new ClassAnalysisHook() {
            @Override
            public Boolean checkImplemented(AbstractionProvider provider, ReferenceInfo ref) throws Throwable {
                if (!ref.isField() || !ref.isStatic()) // nothing to say
                    return null;

                // find field
                Field field = ReflectUtil.getClass(ref.className()).getField(ref.name());
                field.setAccessible(true);

                // check field set
                return field.get(null) != null;
            }
        };
    }

    /** Automatically register impl classes when loaded by this manager */
    public static ClassAnalysisHook autoRegisterLoadedImplClasses() {
        return new ClassAnalysisHook() {
            @Override
            public void onClassLoad(AbstractionProvider provider, Class<?> klass) {
                provider.abstractionManager().registerImpl(klass);
            }
        };
    }

}
