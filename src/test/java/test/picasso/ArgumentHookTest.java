package test.picasso;

import tools.redstone.picasso.AbstractionProvider;
import tools.redstone.picasso.analysis.*;
import tools.redstone.picasso.usage.Abstraction;
import tools.redstone.picasso.usage.Usage;
import tools.redstone.picasso.util.asm.ASMUtil;

import java.util.*;

public class ArgumentHookTest {

    public static void main(String[] args) {
        TestSystem.runTests(ArgumentHookTest.class, true);
    }

    public interface ArgumentLike {
        int idk();
    }

    public static class Argument implements ArgumentLike {
        public int bound;

        public Argument(int bound) {
            this.bound = bound;
        }

        @Override
        public int idk() {
            return new Random().nextInt(bound);
        }
    }

    public interface CommandContext extends Abstraction {
        default int get(ArgumentLike argument) {
            return argument.idk();
        }

        default String a() { return unimplemented(); }
        default String b() { return unimplemented(); }
        default String c() { return unimplemented(); }
        default String d() { return "DDDDDD"; }
    }

    /** Example impl */
    public static class CommandContextImpl implements CommandContext {
        @Override
        public String a() {
            return "AAAAAA";
        }
    }

    public static class ArgumentUsageHook implements ClassAnalysisHook {
        // Registers whether fields should be registered or not
        final Set<ReferenceInfo> excludeFields = new HashSet<>();

        final Map<ReferenceInfo, ReferenceHook> referenceHooksByField = new HashMap<>();

        public ReferenceHook refHook(AnalysisContext context, ReferenceAnalysis called, boolean optional) {
            if (!called.isField())
                return null;
            ReferenceInfo fieldInfo = called.ref;

            // check for cached hook
            ReferenceHook hook = referenceHooksByField.get(fieldInfo);
            if (hook != null)
                return hook;

            // check for argument field type
            if (!ArgumentLike.class.isAssignableFrom(ASMUtil.asClass(called.ref.type())))
                return null;

            // return the hook
            referenceHooksByField.put(fieldInfo, hook = new ReferenceHook() {
                int referenceCounter = 0;

                @Override
                public void requiredReference(AnalysisContext context) {
                    referenceCounter++;
                }

                @Override
                public void optionalReference(AnalysisContext context) {
                    referenceCounter++;
                }

                @Override
                public void optionalBlockDiscarded(AnalysisContext context) {
                    referenceCounter -= 2; // usages in optionally() blocks call both
                                           // referenceRequired() and referenceOptional()
                }

                @Override
                public void postAnalyze() {
                    if (referenceCounter <= 0)
                        excludeFields.add(fieldInfo);
                }
            });

            return hook;
        }

        @Override
        public ReferenceHook optionalReference(AnalysisContext context, ReferenceAnalysis called) {
            return refHook(context, called, true);
        }

        @Override
        public ReferenceHook requiredReference(AnalysisContext context, ReferenceAnalysis called) {
            return refHook(context, called, false);
        }
    }

    /* --------------------------------------------------- */

    public interface Tests {
        void testA(CommandContext ctx);
    }

    public static class TestClass implements Tests {
        Argument a;
        Argument b;
        Argument c;

        @Override
        public void testA(CommandContext ctx) {
            ctx.get(a); // This should be required

            Usage.optionally(() -> {
                ctx.a();
                ctx.get(b); // The should be registered because ctx.a() is implemented
            });

            Usage.optionally(() -> {
                ctx.b();
                ctx.get(c); // This should not be registered because ctx.b() is not implemented
            });
        }
    }

    @TestSystem.Test(testClass = "TestClass", abstractionImpl = "CommandContextImpl", hooks = {"ArgumentUsageHook"})
    void test_ArgHook(ArgumentUsageHook hook, Tests tests, CommandContext ctx, AbstractionProvider abstractionManager) {
        System.out.println(" ⚠ Exclude Argument fields: " + hook.excludeFields);
        // todo: write actual tests
        //  rn just manually review the output of println
    }

}
