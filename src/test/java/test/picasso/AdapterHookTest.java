package test.picasso;

import org.junit.jupiter.api.Assertions;
import tools.redstone.picasso.AbstractionProvider;
import tools.redstone.picasso.HandleAbstraction;
import tools.redstone.picasso.adapter.Adapter;
import tools.redstone.picasso.adapter.AdapterRegistry;
import tools.redstone.picasso.usage.Abstraction;

public class AdapterHookTest {

    public static void main(String[] args) {
        TestSystem.runTests(AdapterHookTest.class, true);
    }

    public interface A extends Abstraction { B getB(); }
    public interface B extends Abstraction { String hello(); }
    public static class InternalA { public InternalB getB() { return new InternalB(); } }
    public static class InternalB { public final String hello = "HELLO"; }

    public static class AImpl extends HandleAbstraction<InternalA> implements A {
        public AImpl(InternalA handle) {
            super(handle);
        }

        @Override
        public B getB() {
            return adapt(handle().getB());
        }
    }

    public static class BImpl extends HandleAbstraction<InternalB> implements B {
        public BImpl(InternalB handle) {
            super(handle);
        }

        @Override
        public String hello() {
            return handle().hello;
        }
    }

    static class test_AdapterHooks {
        void run(AbstractionProvider mgr) {
            /* test code */
            A a = new AImpl(new InternalA());
            B b = a.getB();

            Assertions.assertEquals(b.hello(), "HELLO");
        }
    }

    {
        // setup adapters
        AdapterRegistry.registerHandleAdapter(InternalA.class, A.class);
        AdapterRegistry.registerHandleAdapter(InternalA.class, B.class);
    }

    @TestSystem.Test(autoRegisterImpls = true)
    void test_AdapterHooks(TestSystem.TestInterface itf) {
        // execute run
        itf.runTransformed("run", itf.abstractionManager());
    }

}
