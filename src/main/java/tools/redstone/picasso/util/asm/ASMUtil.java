package tools.redstone.picasso.util.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import tools.redstone.picasso.analysis.ReferenceInfo;
import tools.redstone.picasso.util.ReflectUtil;

import java.lang.reflect.Array;

public class ASMUtil {

    public static final int ASM_V = Opcodes.ASM9;

    /**
     * Find the method node by the given name and descriptor in the given class.
     *
     * @return The node or null if not found.
     */
    public static MethodNode findMethod(ClassNode node, String name, String descriptor) {
        for (MethodNode m : node.methods) {
            if (m.name.equals(name) && m.desc.equals(descriptor)) {
                return m;
            }
        }

        return null;
    }

    public static MethodNode findMethod(ClassNode node, ReferenceInfo info) {
        return findMethod(node, info.name(), info.descriptor());
    }

    public static FieldNode findField(ClassNode node, String name) {
        for (FieldNode f : node.fields) {
            if (name.equals(f.name)) {
                return f;
            }
        }

        return null;
    }

    /** java.lang.Class[] -> asm.Type[] */
    public static Type[] asTypes(Class<?>[] classes) {
        Type[] types = new Type[classes.length];
        for (int i = 0, n = classes.length; i < n; i++)
            types[i] = Type.getType(classes[i]);
        return types;
    }

    /** asm.Type[] -> java.lang.Class[] */
    public static Class<?>[] asClasses(Type[] types) {
        Class<?>[] classes = new Class[types.length];
        for (int i = 0; i < types.length; i++)
            classes[i] = asClass(types[i]);
        return classes;
    }

    /** asm.Type -> java.lang.Class */
    public static Class<?> asClass(Type type) {
        return switch (type.getSort()) {
            case Type.ARRAY -> Array.newInstance(asClass(type), 0).getClass();
            case Type.BOOLEAN -> boolean.class;
            case Type.BYTE -> byte.class;
            case Type.CHAR -> char.class;
            case Type.DOUBLE -> double.class;
            case Type.FLOAT -> float.class;
            case Type.INT -> int.class;
            case Type.LONG -> long.class;
            case Type.SHORT -> short.class;
            case Type.VOID -> void.class;
            case Type.OBJECT -> ReflectUtil.getClass(type.getClassName());
            case Type.METHOD -> asClass(type.getReturnType());
            default -> throw new AssertionError();
        };
    }

    public static void makeDebugLog(MethodVisitor v, String msg) {
        v.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        v.visitLdcInsn(msg);
        v.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
    }

    public static void forceVerifyError(MethodVisitor v) {
        v.visitMethodInsn(Opcodes.INVOKESTATIC, "0", "0", "(IIIIIIIIIIIIIIIIIIIIIII)V");
    }

}
