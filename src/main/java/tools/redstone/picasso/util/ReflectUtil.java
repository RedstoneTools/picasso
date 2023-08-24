package tools.redstone.picasso.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import sun.misc.Unsafe;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A utility class for transforming and loading classes.
 */
public class ReflectUtil {
    private ReflectUtil() { }

    static final Map<String, Class<?>> forNameCache = new HashMap<>();

    // The sun.misc.Unsafe instance
    static final Unsafe UNSAFE;

    /* Method handles for cracking  */
    static final MethodHandle SETTER_Field_modifiers;
    static final MethodHandle ClassLoader_findLoadedClass;
    static final MethodHandle ClassLoader_addClass;
    static final MethodHandles.Lookup INTERNAL_LOOKUP;

    static {
        try {
            // get using reflection
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (Exception e) {
            // rethrow error
            throw new ExceptionInInitializerError(e);
        }

        try {
            {
                // get lookup
                Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
                MethodHandles.publicLookup();
                INTERNAL_LOOKUP = (MethodHandles.Lookup)
                        UNSAFE.getObject(
                                UNSAFE.staticFieldBase(field),
                                UNSAFE.staticFieldOffset(field)
                        );
            }

            SETTER_Field_modifiers = INTERNAL_LOOKUP.findSetter(Field.class, "modifiers", Integer.TYPE);
            ClassLoader_findLoadedClass = INTERNAL_LOOKUP.findVirtual(ClassLoader.class, "findLoadedClass", MethodType.methodType(Class.class, String.class));
            ClassLoader_addClass = INTERNAL_LOOKUP.findVirtual(ClassLoader.class, "addClass", MethodType.methodType(void.class, Class.class));
        } catch (Throwable t) {
            // throw exception in init
            throw new ExceptionInInitializerError(t);
        }
    }

    public static MethodHandles.Lookup getInternalLookup() {
        return INTERNAL_LOOKUP;
    }

    public static Unsafe getUnsafe() {
        return UNSAFE;
    }

    /**
     * Get the loaded class by the given name.
     *
     * @param name The class name.
     * @throws IllegalArgumentException If no class by that name exists.
     * @return The class.
     */
    public static Class<?> getClass(String name) {
        Class<?> klass = forNameCache.get(name);
        if (klass != null)
            return klass;

        try {
            forNameCache.put(name, klass = Class.forName(name));
            return klass;
        } catch (Exception e) {
            throw new IllegalArgumentException("No class by name '" + name + "'", e);
        }
    }

    /**
     * Get the loaded class by the given name.
     *
     * @param name The class name.
     * @param loader The loader to load the class with.
     * @throws IllegalArgumentException If no class by that name exists.
     * @return The class.
     */
    public static Class<?> getClass(String name, ClassLoader loader) {
        Class<?> klass = forNameCache.get(name);
        if (klass != null)
            return klass;

        try {
            forNameCache.put(name, klass = Class.forName(name, true, loader));
            return klass;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error while finding class by name '" + name + "'", e);
        }
    }

    /**
     * Get the bytes of the class file of the given loaded class.
     *
     * @param klass The class.
     * @return The bytes.
     */
    public static byte[] getBytes(Class<?> klass) {
        try {
            // get resource path
            String className = klass.getName();
            String classAsPath = className.replace('.', '/') + ".class";

            // open resource
            try (InputStream stream = klass.getClassLoader().getResourceAsStream(classAsPath)) {
                if (stream == null)
                    throw new IllegalArgumentException("Could not find resource stream for " + klass);
                return stream.readAllBytes();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error occurred", e);
        }
    }

    public static ClassReader reader(Class<?> klass) {
        return new ClassReader(getBytes(klass));
    }

    /**
     * Set the modifiers of the given field.
     *
     * @param f The field.
     * @param mods The modifiers.
     */
    public static void setModifiers(Field f, int mods) {
        try {
            SETTER_Field_modifiers.invoke(f, mods);
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to set modifiers of " + f, t);
        }
    }

    /**
     * Analyze the given class using the given class visitor.
     *
     * @param klass The class.
     * @param visitor The visitor.
     */
    public static void analyze(Class<?> klass, ClassVisitor visitor) {
        byte[] bytes = getBytes(klass);
        ClassReader reader = new ClassReader(bytes);
        reader.accept(visitor, 0);
    }

    /** Defines a class transformer. */
    public interface ClassTransformer {
        void transform(String name, ClassReader reader, ClassWriter writer);
    }

    /**
     * Find the loaded class lowest in the chain of class loaders.
     *
     * @param loader The loader.
     * @param name The class name.
     * @return The loaded class or null if unloaded.
     */
    public static Class<?> findLoadedClassInParents(ClassLoader loader, String name) {
        try {
            while (loader != null) {
                Class<?> klass = (Class<?>) ClassLoader_findLoadedClass.invoke(loader, name);
                if (klass != null) {
                    return klass;
                }

                loader = loader.getParent();
            }

            return null;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public static Class<?> findLoadedClass(ClassLoader loader, String name) {
        try {
            return (Class<?>) ClassLoader_findLoadedClass.invoke(loader, name);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    /** Black hole for class objects */
    public static boolean ensureLoaded(Class<?> klass) {
        if (klass == null) return false;
        return klass.hashCode() != 0;
    }

    public static ClassLoader rootClassLoader(ClassLoader loader) {
        while (loader.getParent() != null) {
            loader = loader.getParent();
        }

        return loader;
    }

    public static ClassLoader transformingClassLoader(Predicate<String> namePredicate,
                                                      ClassTransformer transformer,
                                                      int writerFlags) {
        return transformingClassLoader(namePredicate, ClassLoader.getSystemClassLoader(), transformer, writerFlags, false, null);
    }

    public static ClassLoader transformingClassLoader(Predicate<String> namePredicate,
                                                      ClassLoader parent,
                                                      ClassTransformer transformer,
                                                      int writerFlags,
                                                      boolean warnLoaded,
                                                      Consumer<Class<?>> postLoad) {
        // create class loader
        return new ClassLoader(parent) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (!namePredicate.test(name)) {
                    return super.loadClass(name);
                }

                Class<?> klass = ReflectUtil.findLoadedClassInParents(this, name);
                if (klass != null) {
                    if (warnLoaded && klass.getClassLoader() != this) {
                        System.out.println("WARNING Found loaded class " + name + " in loader " + klass.getClassLoader());
                    }

                    return klass;
                }

                try {
                    String classAsPath = name.replace('.', '/') + ".class";

                    // open resource
                    try (InputStream stream = getResourceAsStream(classAsPath)) {
                        if (stream == null)
                            throw new IllegalArgumentException("Could not find resource stream for " + klass);
                        byte[] bytes = stream.readAllBytes();

                        ClassReader reader = new ClassReader(bytes);
                        ClassWriter writer = new ClassWriter(writerFlags);
                        transformer.transform(name, reader, writer);
                        bytes = writer.toByteArray();

                        // define the class
                        klass = defineClass(name, bytes, 0, bytes.length);
                        if (postLoad != null)
                            postLoad.accept(klass);

                        return klass;
                    }
                } catch (Throwable t) {
                    throw new RuntimeException("While loading class " + name, t);
                }
            }

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                Class<?> klass = this.findLoadedClass(name);
                if (klass != null) {
                    return klass;
                }

                return super.findClass(name);
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <T, T2> T2[] arrayCast(T[] arr, Class<T2> t2Class) {
        T2[] arr2 = (T2[]) Array.newInstance(t2Class, arr.length);
        for (int i = 0, n = arr.length; i < n; i++)
            arr2[i] = (T2) arr[i];
        return arr2;
    }

}
