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
    MethodHandle MH_HandleAbstraction_handle =
            ThrowingSupplier.safe(() -> ReflectUtil.getInternalLookup()
            .findVirtual(HandleAbstraction.class, "handle", MethodType.methodType(Object.class)));

    static <A, B> Adapter<A, B> of(Class<? extends A> aClass,
                                   Class<? extends B> bClass,
                                   Function<B, A> toA,
                                   Function<A, B> toB) {
        return new Adapter<A, B>() {
            @Override
            public Class<? extends A> aClass() {
                return aClass;
            }

            @Override
            public Class<? extends B> bClass() {
                return bClass;
            }

            @Override
            public A toA(B val) {
                return toA.apply(val);
            }

            @Override
            public B toB(A val) {
                return toB.apply(val);
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <A, H> Adapter<H, A> handle(Class<H> handleClass, Class<A> abstractionClass) {
        try {
            return new Adapter<>() {
                Class<?> implClass;
                MethodHandle methodGetHandle;
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
                    try {
                        return (H) MH_HandleAbstraction_handle.invoke(val);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public A toB(H val) {
                    if (implClass == null) {
                        implClass = AbstractionManager.getInstance().getImplByClass(abstractionClass);
                        if (implClass == null)
                            throw new RuntimeException("Could not find impl class for " + abstractionClass);
                    }

                    try {
                        if (constructImpl == null) {
                            constructImpl = ReflectUtil.getInternalLookup()
                                    .findConstructor(implClass, MethodType.methodType(void.class, handleClass));
                        }

                        return (A) constructImpl.invoke(val);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
            };
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    Class<? extends A> aClass();
    Class<? extends B> bClass();

    A toA(B val);
    B toB(A val);
}
