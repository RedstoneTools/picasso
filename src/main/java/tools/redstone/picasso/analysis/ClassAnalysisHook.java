package tools.redstone.picasso.analysis;

import org.objectweb.asm.Type;
import tools.redstone.picasso.AbstractionProvider;
import tools.redstone.picasso.util.asm.MethodWriter;

/**
 * Used to extend functionality of the dependency analyzer.
 */
public interface ClassAnalysisHook {

    interface ReferenceHook {
        // When the optional block this method was
        // called in is discarded
        default void optionalBlockDiscarded(AnalysisContext context) { }

        // When the method is called through required means
        default void requiredReference(AnalysisContext context) { }

        // When the method is called through an optional block
        default void optionalReference(AnalysisContext context) { }

        // Called in post-analysis
        default void postAnalyze() { }
    }

    interface MethodVisitorHook {
        /* All methods return whether they intercepted something */
        default boolean visitMethodInsn(AnalysisContext ctx, int opcode, ReferenceInfo info) { return false; }
        default boolean visitInsn(AnalysisContext ctx, int opcode) { return false; }
        default boolean visitTypeInsn(AnalysisContext ctx, int opcode, Type type) { return false; }
        default boolean visitVarInsn(AnalysisContext ctx, int opcode, int varIndex, Type type, String signature) { return false; }
        default boolean visitFieldInsn(AnalysisContext ctx, int opcode, ReferenceInfo fieldInfo) { return false; }

        default void visitEnd() { }
    }

    // When this hook is registered to the given provider
    default void onRegister(AbstractionProvider provider) { }

    // When a class is newly loaded through the abstraction provider's
    // transforming class loader.
    default void onClassLoad(AbstractionProvider provider, Class<?> klass) { }

    // When a method is referenced in a required block
    default ReferenceHook requiredReference(AnalysisContext context, ReferenceAnalysis called) { return null; }

    // When a method is referenced in an optional block
    default ReferenceHook optionalReference(AnalysisContext context, ReferenceAnalysis called) { return null; }

    // When a new method is entered to be analyzed
    default void enterMethod(AnalysisContext context) { }

    // When a new method is entered to be analyzed and transformed, you can hook into it
    default MethodVisitorHook visitMethod(AnalysisContext context, MethodWriter writer) { return null; }

    // When a method has finished analyzing
    default void leaveMethod(AnalysisContext context) { }

    // Is dependency checks
    default Boolean isDependencyCandidate(AnalysisContext context, ReferenceInfo ref) { return null; }

    // Dependency presence checks
    default Boolean checkImplemented(AbstractionProvider provider, ReferenceInfo ref) throws Throwable { return null; }

}
