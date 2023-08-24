package test.picasso;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import tools.redstone.picasso.AbstractionProvider;
import tools.redstone.picasso.analysis.ReferenceInfo;
import tools.redstone.picasso.usage.Abstraction;
import tools.redstone.picasso.usage.NoneImplementedException;
import tools.redstone.picasso.usage.NotImplementedException;
import tools.redstone.picasso.usage.Usage;

public class SimpleDependencyTest {

    public static void main(String[] args) throws Throwable {
        TestSystem.runTests(SimpleDependencyTest.class, true);
    }

    /* --------------------------------------------------- */

    /** Example abstraction */
    public interface Abc extends Abstraction {
        String IMPLEMENTED = "ABC";
        String UNIMPLEMENTED = null;
        String UNIMPLEMENTED2 = null;
        default String a() { return unimplemented(); }
        default String b() { return unimplemented(); }
        default String c() { return unimplemented(); }
        default String d() { return "DDDDDD"; }
        default String e() { return unimplemented(); }
    }

    /** Example impl */
    public static class AbcImpl implements Abc {
        @Override
        public String a() {
            return "AAAAAA";
        }
    }

    /* --------------------------------------------------- */

    public interface Tests {
        String testA(Abc abc);
        String testB(Abc abc);
        String testC(Abc abc);
        String testD(Abc abc);
        String testE(Abc abc);
        String testF(Abc abc);
        String testG(Abc abc);
        String testH(Abc abc);
    }

    /** The class with test code */
    @Disabled
    public static class TestClass implements Tests {
        String deep(Abc abc) {
            return abc.c();
        }

        public String testA(Abc abc) {
            return abc.a();
        }

        public String testB(Abc abc) {
            return Usage.either(() -> deep(abc), abc::b, abc::d);
        }

        @Override
        public String testC(Abc abc) {
            return abc.b();
        }

        @Override
        public String testD(Abc abc) {
            return Usage.either(abc::b, abc::c);
        }

        @Override
        public String testE(Abc abc) {
            return Usage.optionally(() -> abc.e())
                    .orElse("ABC");
        }

        @Override
        public String testF(Abc abc) {
            return Abc.IMPLEMENTED;
        }

        @Override
        public String testG(Abc abc) {
            return Usage.optionally(() -> Abc.UNIMPLEMENTED)
                    .orElse("ABC");
        }

        @Override
        public String testH(Abc abc) {
            return Abc.UNIMPLEMENTED2;
        }
    }

    @TestSystem.Test(testClass = "TestClass", abstractionImpl = "AbcImpl")
    void test_Unimplemented(Tests testInstance, AbstractionProvider abstractionManager, Abc abc) throws Throwable {
        Assertions.assertTrue(abstractionManager.isImplemented(ReferenceInfo.forMethodInfo(Abc.class, "d", false, String.class)));
        Assertions.assertTrue(abstractionManager.isImplemented(ReferenceInfo.forMethodInfo(Abc.class, "a", false, String.class)));
        Assertions.assertFalse(abstractionManager.isImplemented(ReferenceInfo.forMethodInfo(Abc.class, "c", false, String.class)));
        Assertions.assertFalse(abstractionManager.allImplemented(testInstance.getClass()));
        Assertions.assertDoesNotThrow(() -> testInstance.testA(abc));
        Assertions.assertDoesNotThrow(() -> testInstance.testB(abc));
        Assertions.assertEquals(abc.d(), testInstance.testB(abc));
        Assertions.assertThrows(NotImplementedException.class, () -> testInstance.testC(abc));
        Assertions.assertThrows(NoneImplementedException.class, () -> testInstance.testD(abc));
        Assertions.assertEquals("ABC", testInstance.testE(abc));
        Assertions.assertDoesNotThrow(() -> testInstance.testF(abc));
        Assertions.assertEquals("ABC", testInstance.testG(abc));
        Assertions.assertThrows(NotImplementedException.class, () -> testInstance.testH(abc));
        TestSystem.assertDependenciesEquals(abstractionManager.getClassAnalysis(testInstance.getClass()).dependencies, "required Abc.a", "required Abc.b", "required Abc.d", "optional Abc.c", "optional Abc.e", "optional Abc.UNIMPLEMENTED", "required Abc.UNIMPLEMENTED2", "none", "one Abc.d");
    }

}
