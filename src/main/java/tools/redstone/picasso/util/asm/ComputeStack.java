package tools.redstone.picasso.util.asm;

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import tools.redstone.picasso.analysis.ReferenceInfo;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static org.objectweb.asm.Opcodes.*;

/**
 * Tracks the compute stack of a method.
 */
public class ComputeStack extends Stack<Object> {

    /**
     * The values of locals which were tracked.
     */
    public final List<Object> localValues = new ArrayList<>();

    public interface Value {
        /** Get this type of the value */
        Type type();

        /** Get the generic signature of this value */
        default String signature() { return type().toString(); }
    }

    public Object popOrNull() {
        return isEmpty() ? null : pop();
    }

    public Object peekOrNull() {
        return isEmpty() ? null : peek();
    }

    @SuppressWarnings("unchecked")
    public <V extends Value> V popAs() {
        return (V) popOrNull();
    }

    @SuppressWarnings("unchecked")
    public <V2> V2 expectAndPop(Class<V2> vClass) {
        if (isEmpty()) {
            throw new IllegalArgumentException("Expected value of type " + vClass.getSimpleName() + ", got none");
        }

        var val = pop();
        if (!vClass.isInstance(val)) {
            throw new IllegalArgumentException("Expected value of type " + vClass.getSimpleName() + ", got " + val.getClass().getSimpleName());
        }

        return (V2) val;
    }

    public Object getVarValue(int varIndex) {
        if (varIndex >= localValues.size())
            return null;
        return localValues.get(varIndex);
    }

    public void putVarValue(int varIndex, Object value) {
        if (varIndex >= localValues.size())
            for (int i = localValues.size() - 1; i < varIndex; i++)
                localValues.add(null);
        localValues.set(varIndex, value);
    }

    /** Represents a constant value */
    public record Constant(Type type, Object value) implements Value {

    }

    /** Represents an array value */
    public record Array(Type type, Object[] array) implements Value {
        public Type elementType() {
            return type.getElementType();
        }
    }

    /** Represents a field sourced from the  */
    public record FieldValue(ReferenceInfo fieldInfo) implements Value {
        @Override public Type type() { return fieldInfo.type(); }
        @Override public String signature() { return fieldInfo.signature(); }
    }

    /** Represents an instance of the given type */
    public record InstanceOf(Type type) implements Value { }

    /** Represents the return value of a method */
    public record ReturnValue(ReferenceInfo method, Type type, String signature) implements Value {
        public static ReturnValue of(ReferenceInfo info) {
            return new ReturnValue(info, info.type().getReturnType(), info.signature());
        }
    }

    /** Represents a value sourced from a local variable */
    public record LocalValue(int varIndex, Type type, String signature, Object value) implements Value { }

    public static class TrackingMethodVisitor<P extends MethodVisitor> extends MethodVisitor {

        protected final P parent;               // The parent visitor/writer
        private final ComputeStack stack;       // The stack to emulate the results on
        private final MethodNode dataNode;      // The node to source data like locals from
        private final ReferenceInfo thisMethod; // The reference info for this method

        protected TrackingMethodVisitor(P parent, ComputeStack stack, MethodNode dataNode, ReferenceInfo thisMethod) {
            super(ASMUtil.ASM_V, parent);
            this.parent = parent;
            this.stack = stack;
            this.dataNode = dataNode;
            this.thisMethod = thisMethod;
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            final var ref = ReferenceInfo.forMethodInfo(bootstrapMethodHandle.getOwner(), bootstrapMethodHandle.getName(), descriptor, bootstrapMethodHandle.getTag() == H_INVOKESTATIC);

            if (!ref.isStatic())
                // pop instance val
                stack.pop();

            for (int i = 0, n = ref.type().getArgumentTypes().length; i < n; i++) {
                stack.pop();
            }

            var retType = ref.type().getReturnType();
            if (retType.getSort() != Type.VOID)
                stack.push(new ReturnValue(ref, retType.getReturnType(), retType.getDescriptor()));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            final var ref = ReferenceInfo.forMethodInfo(owner, name, descriptor, opcode == INVOKESTATIC);

            if (!ref.isStatic())
                // pop instance val
                stack.pop();

            for (int i = 0, n = ref.type().getArgumentTypes().length; i < n; i++) {
                stack.pop();
            }

            var retType = ref.type().getReturnType();
            if (retType.getSort() != Type.VOID)
                stack.push(new ReturnValue(ref, retType, retType.getDescriptor()));
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            super.visitVarInsn(opcode, varIndex);

            if (!thisMethod.isStatic() && varIndex == 0) {
                stack.push(new ComputeStack.InstanceOf(Type.getObjectType(thisMethod.internalClassName())));
                return;
            }

            LocalVariableNode var = dataNode.localVariables.get(varIndex);
            switch (opcode) {
                case ALOAD, ILOAD, FLOAD, DLOAD
                        -> stack.push(new LocalValue(varIndex, Type.getType(var.desc), var.signature, stack.getVarValue(varIndex)));
                case ASTORE, ISTORE, FSTORE, DSTORE
                        -> stack.putVarValue(varIndex, stack.popOrNull());
            }
        }

        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(value);
            stack.push(new Constant(Type.getType(value.getClass()), value));
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            super.visitIntInsn(opcode, operand);
            switch (opcode) {
                case BIPUSH, SIPUSH -> stack.push(new Constant(Type.INT_TYPE, operand));
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, type);
            switch (opcode) {
                case NEW -> stack.push(new InstanceOf(Type.getObjectType(type)));
                case ANEWARRAY -> {
                    int len = stack.expectAndPop(Integer.class);
                    stack.push(new Array(Type.getType("[L" + type + ";"), new Object[len]));
                }

                case CHECKCAST -> {
                    if (!stack.isEmpty())
                        stack.pop();
                    stack.push(new ComputeStack.InstanceOf(Type.getObjectType(type)));
                }
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode, owner, name, descriptor);
            switch (opcode) {
                case PUTSTATIC, PUTFIELD -> {
                    if (opcode == PUTFIELD) stack.pop();
                    stack.pop();
                }

                case GETSTATIC, GETFIELD -> {
                    final var ref = ReferenceInfo.forFieldInfo(owner, name, descriptor, opcode == GETSTATIC);

                    if (opcode == GETFIELD) stack.pop();
                    stack.push(new FieldValue(ref));
                }
            }
        }

        @Override
        public void visitInsn(int opcode) {
            super.visitInsn(opcode);
            switch (opcode) {
                case Opcodes.DUP -> stack.push(stack.peek());
                case Opcodes.ACONST_NULL -> stack.push(null);
                case Opcodes.ICONST_0 -> stack.push(0);
                case Opcodes.ICONST_1 -> stack.push(1);
                case Opcodes.ICONST_2 -> stack.push(2);
                case Opcodes.ICONST_3 -> stack.push(3);
                case Opcodes.ICONST_4 -> stack.push(4);
                case Opcodes.ICONST_5 -> stack.push(5);
                case Opcodes.POP, Opcodes.ARETURN, Opcodes.IRETURN, Opcodes.DRETURN, Opcodes.FRETURN -> {
                    stack.popOrNull();
                }
                case Opcodes.POP2 -> {
                    stack.pop();
                    stack.pop();
                }
                case Opcodes.AASTORE -> {
                    Object val = stack.pop();
                    int idx = (int) stack.pop();
                    Array arr = stack.expectAndPop(Array.class);
                    arr.array[idx] = val;
                }
            }
        }
    }

}
