package tools.redstone.picasso.adapter;

import tools.redstone.picasso.AbstractionManager;
import tools.redstone.picasso.HandleAbstraction;
import tools.redstone.picasso.util.ReflectUtil;
import tools.redstone.picasso.util.functional.ThrowingSupplier;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.function.Function;

/**
 * Bi-directional adapter.
 *
 * @param <A> First type.
 * @param <B> Second type.
 */
public interface Adapter<A, B> {

    /**
     * Creates an adapter for the given handle and abstraction classes.
     *
     * This assumes that the abstraction class is implemented by a
     * {@link HandleAbstraction}-based implementation with a constructor
     * which takes as it's only argument the handle at first use.
     *
     * @param handleClass The handle class.
     * @param abstractionClass The abstraction class.
     * @param <A> The abstraction type.
     * @param <H> The handle type.
     * @return The adapter for A -> H.
     */
    @SuppressWarnings("unchecked")
    static <A, H> Adapter<H, A> forHandleAbstraction(Class<H> handleClass, Class<A> abstractionClass) {
        try {
            return new Adapter<>() {
                Class<?> implClass;
                MethodHandle constructImpl;

                @Override
                public Class<? extends H> aClass() {
                    return handleClass;
                }

                @Override
                public Class<? extends A> bClass() {
                    return abstractionClass;
                }

                @Override
                public H toA(A val) {
                    return ((HandleAbstraction<H>) val).handle();
                }

                @Override
                public A toB(H val) {
                    try {
                        if (implClass == null) {
                            // find impl class if not already done
                            implClass = AbstractionManager.getInstance().getImplByClass(abstractionClass);
                            if (implClass == null)
                                throw new RuntimeException("Could not find impl class for " + abstractionClass);

                            // assume the presence of a (Handle;)V constructor
                            constructImpl = ReflectUtil.getInternalLookup()
                                    .findConstructor(implClass, MethodType.methodType(void.class, handleClass));
                        }

                        return (A) constructImpl.invoke(val);
                    } catch (Throwable e) {
                        throw new RuntimeException("" + e);
                    }
                }
            };
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /**
     * Get the runtime type of class A.
     *
     * @return The class object for A.
     */
    Class<? extends A> aClass();

    /**
     * Get the runtime type of class B.
     *
     * @return The class object for B;
     */
    Class<? extends B> bClass();

    /**
     * Convert the given value B to type A.
     *
     * @param val B.
     * @return A.
     */
    A toA(B val);

    /**
     * Convert the given value A to type B.
     *
     * @param val A.
     * @return B.
     */
    B toB(A val);

}
