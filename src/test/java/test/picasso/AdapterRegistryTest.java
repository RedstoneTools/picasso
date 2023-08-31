package test.picasso;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.redstone.picasso.adapter.Adapter;
import tools.redstone.picasso.adapter.AdapterRegistry;
import tools.redstone.picasso.adapter.DynamicAdapterRegistry;

public class AdapterRegistryTest {

    class MockAdapter<A, B> implements Adapter<A, B> {
        final Class<? extends A> aClass;
        final Class<? extends B> bClass;

        MockAdapter(Class<? extends A> aClass, Class<? extends B> bClass) {
            this.aClass = aClass;
            this.bClass = bClass;
        }

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
            return null;
        }

        @Override
        public B toB(A val) {
            return null;
        }
    }

    static class A { }           // corresponds to foo
    static class B extends A { } // corresponds to bar
    static class C { }           // corresponds to baz
    static class D extends C { } // corresponds to last

    interface Foo { }
    interface Bar extends Foo { }
    interface Baz extends Bar { }
    interface Last extends Baz { }

    @Test
    void test_AdapterRegistry() {
        // setup
        AdapterRegistry registry = new DynamicAdapterRegistry();
        registry.register(new MockAdapter<>(A.class, Foo.class));
        registry.register(new MockAdapter<>(B.class, Bar.class));
        registry.register(new MockAdapter<>(C.class, Baz.class));

        // assertions
        Assertions.assertNotNull(registry.findAdapterFunction(A.class, Foo.class));
        Assertions.assertNotNull(registry.findAdapterFunction(Foo.class, A.class));
        Assertions.assertNull(registry.findAdapterFunction(D.class, Last.class));
        Assertions.assertEquals(C.class, registry.findAdapterFunction(C.class, Baz.class).srcClass());
        Assertions.assertEquals(C.class, registry.findAdapterFunction(D.class, Baz.class).srcClass());
        Assertions.assertNull(registry.findAdapterFunction(Last.class, D.class));
    }

}
