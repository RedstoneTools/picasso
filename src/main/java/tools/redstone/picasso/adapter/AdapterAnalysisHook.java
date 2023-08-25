package tools.redstone.picasso.adapter;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import tools.redstone.picasso.AbstractionProvider;
import tools.redstone.picasso.analysis.*;
import tools.redstone.picasso.util.asm.ASMUtil;
import tools.redstone.picasso.util.asm.ComputeStack;
import tools.redstone.picasso.util.asm.MethodWriter;

import java.util.function.Function;

public class AdapterAnalysisHook implements ClassAnalysisHook {

    static final Type TYPE_Object = Type.getType(Object.class);
    static final Type TYPE_AdapterRegistry = Type.getType(AdapterRegistry.class);
    static final Type TYPE_Function = Type.getType(Function.class);
    static final String NAME_Function = TYPE_Function.getInternalName();

    private final AdapterRegistry adapterRegistry;                               // The adapter registry to source adapters from
    private int adapterIdCounter = 0;                                            // The counter for the $$adapter_xx fields
    private final AbstractionProvider.ClassInheritanceChecker inheritanceChecker; // The inheritance checker to check for `adapt` calls

    public AdapterAnalysisHook(Class<?> adaptMethodOwner, AdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
        inheritanceChecker = AbstractionProvider.ClassInheritanceChecker.forClass(adaptMethodOwner);
    }

    static class TrackedReturnValue implements ComputeStack.Value {
        final ComputeStack.ReturnValue returnValue; // The analyzer return value
        String dstType;                                        // The destination type

        TrackedReturnValue(ComputeStack.ReturnValue returnValue) {
            this.returnValue = returnValue;
        }

        @Override
        public Type type() {
            return returnValue.type();
        }

        @Override
        public String signature() {
            return returnValue.signature();
        }
    }

    @Override
    public MethodVisitorHook visitMethod(AnalysisContext context, MethodWriter writer) {
        final ReferenceInfo currMethod = context.currentMethod();
        final ReferenceAnalysis currAnalysis = context.currentAnalysis();
        return new MethodVisitorHook() {
            @Override
            public boolean visitMethodInsn(AnalysisContext ctx, int opcode, ReferenceInfo info) {
                // check for #adapt(Object)
                if (inheritanceChecker.checkClassInherits(ctx.abstractionProvider(), info.className()) && info.name().equals("adapt")) {
                    boolean isStatic = opcode == Opcodes.INVOKESTATIC;
                    Object instanceValue = isStatic ? context.currentComputeStack().pop() : null;
                    Object srcValue = context.currentComputeStack().pop();
                    String srcType = ((ComputeStack.Value)srcValue).signature();

                    // load the src class
                    Type srcAsmType = Type.getType(srcType);
                    if (srcAsmType.getSort() == Type.OBJECT)
                        context.abstractionProvider().findClass(srcAsmType.getClassName());
                    if (srcAsmType.getSort() == Type.METHOD)
                        srcType = srcAsmType.getReturnType().toString();

                    // push tracked return value
                    var trackedReturnValue = new TrackedReturnValue(new ComputeStack.ReturnValue(info, TYPE_Object, TYPE_Object.toString()));
                    context.currentComputeStack().push(trackedReturnValue);

                    // add field to class
                    String fieldName = "$$adapter_" + (adapterIdCounter++);
                    currAnalysis.classNode()
                            // create static field
                            .visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, fieldName, TYPE_Function.getDescriptor(), TYPE_Function.getDescriptor(), null /* set in static initializer */);

                    // modify <cinit>
                    var mCInit = ASMUtil.findMethod(currAnalysis.classNode(), "<clinit>", "()V");
                    boolean created = false;
                    if (mCInit == null) {
                        mCInit = (MethodNode) currAnalysis.classNode().visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "<clinit>", "()V", "()V", new String[] { });
                        created = true;
                        mCInit.visitCode();
                    }

                    boolean finalCreated = created;
                    String finalSrcType = srcType;
                    var clinitInsn = new InsnNode(-1) {
                        @Override
                        public void accept(MethodVisitor v) {
                            String dstType = trackedReturnValue.dstType;
                            if (dstType == null)
                                return;

                            v.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE_AdapterRegistry.getInternalName(), "getInstance", "()L" + TYPE_AdapterRegistry.getInternalName() + ";", false);
                            v.visitLdcInsn(finalSrcType);
                            v.visitLdcInsn(dstType);
                            v.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TYPE_AdapterRegistry.getInternalName(), "lazyRequireMonoDirectional", "(Ljava/lang/String;Ljava/lang/String;)" + TYPE_Function.getDescriptor(), false);
                            v.visitFieldInsn(Opcodes.PUTSTATIC, currMethod.internalClassName(), fieldName, TYPE_Function.getDescriptor());
                            if (finalCreated) v.visitInsn(Opcodes.RETURN);
                        }
                    };

                    if (mCInit.instructions.size() != 0) mCInit.instructions.insertBefore(mCInit.instructions.getFirst(), clinitInsn);
                    else mCInit.instructions.add(clinitInsn);

                    // replace instruction
                    writer.addInsn(v -> {
                        // if, when we come to write this instruction,
                        // the dst type still has not been determined we throw an error
                        String dstType = trackedReturnValue.dstType;
                        if (dstType == null)
                            throw new IllegalStateException("Could not determine dst type for `adapt(value)` call in " + currMethod + " with src type `" + finalSrcType + "`");

                        // load the dst class
                        Type dstAsmType = Type.getType(dstType);
                        if (dstAsmType.getSort() == Type.OBJECT)
                            context.abstractionProvider().findClass(dstAsmType.getClassName());

                        // check if the adapter exists
                        if (adapterRegistry.findMonoDirectional(finalSrcType, dstType) == null)
                            throw new IllegalStateException("No adapter found for src = " + finalSrcType + ", dst = " + dstType + " in method " + currMethod);

                        // pop original instance variable after
                        if (!isStatic) {
                                                       // val - this
                            v.visitInsn(Opcodes.SWAP); // this - val
                            v.visitInsn(Opcodes.POP);  // val
                        }

                        // push adapter and swap
                        v.visitFieldInsn(Opcodes.GETSTATIC, currMethod.internalClassName(), fieldName, TYPE_Function.getDescriptor());
                        v.visitInsn(Opcodes.SWAP); // val - function

                        // make it call Function#apply
                        v.visitMethodInsn(Opcodes.INVOKEINTERFACE, NAME_Function, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
                    });

                    return true;
                }

                // didn't intercept shit
                return false;
            }

            @Override
            public boolean visitVarInsn(AnalysisContext ctx, int opcode, int varIndex, Type type, String signature) {
                if (opcode == Opcodes.ASTORE && ctx.currentComputeStack().peek() instanceof TrackedReturnValue rv) {
                    ctx.currentComputeStack().pop();
                    ctx.currentComputeStack().push(new ComputeStack.LocalValue(varIndex, type, signature, null));

                    rv.dstType = signature;
                    return true;
                }

                return false;
            }

            @Override
            public boolean visitFieldInsn(AnalysisContext ctx, int opcode, ReferenceInfo fieldInfo) {
                if ((opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) &&
                        ctx.currentComputeStack().peekOrNull() instanceof TrackedReturnValue rv) {
                    if (opcode == Opcodes.PUTFIELD) ctx.currentComputeStack().pop();
                    ctx.currentComputeStack().pop();

                    rv.dstType = fieldInfo.signature() == null ? fieldInfo.desc() : fieldInfo.signature();
                    return true;
                }

                return false;
            }

            @Override
            public boolean visitInsn(AnalysisContext ctx, int opcode) {
                if (opcode == Opcodes.ARETURN && ctx.currentComputeStack().peekOrNull() instanceof TrackedReturnValue rv) {
                    ctx.currentComputeStack().pop();

                    rv.dstType = ctx.currentMethod().type().getReturnType().getDescriptor();
                    return true;
                }

                return false;
            }

            @Override
            public boolean visitTypeInsn(AnalysisContext ctx, int opcode, Type type) {
                if (opcode == Opcodes.CHECKCAST && ctx.currentComputeStack().peekOrNull() instanceof TrackedReturnValue rv) {
                    ctx.currentComputeStack().pop();
                    ctx.currentComputeStack().push(new ComputeStack.InstanceOf(type));

                    rv.dstType = type.getDescriptor();
                    return true;
                }

                return false;
            }
        };
    }
}
