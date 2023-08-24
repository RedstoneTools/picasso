package tools.redstone.picasso.analysis;

import org.objectweb.asm.Type;
import tools.redstone.picasso.AbstractionProvider;
import tools.redstone.picasso.util.asm.ASMUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

// ASM method/field information
public class ReferenceInfo {

    final String internalClassName; // The internal (/) owning/referenced class name
    final String className;         // The public/binary (.) owning/referenced class name
    final String name;              // The name of this symbol if it is a member
    final String desc;              // The descriptor of this reference.
    final String signature;         // The generic signature of this reference.
    final Type asmType;             // The ASM Type object derived from the descriptor
    final boolean isStatic;         // Whether this reference was referenced in a static context
    final RefType type;             // The type of this reference/the symbol it's referencing

    // cached hash code
    int hashCode = 0;

    public ReferenceInfo(String internalClassName, String className, String name, String desc, Type type, boolean isStatic, RefType type1) {
        this.internalClassName = internalClassName;
        this.className = className;
        this.name = name;
        this.desc = desc;
        this.asmType = type;
        this.isStatic = isStatic;
        this.type = type1;
        this.signature = null;
    }

    public ReferenceInfo(String internalClassName, String className, String name, String desc, Type type, boolean isStatic, String signature, RefType type1) {
        this.internalClassName = internalClassName;
        this.className = className;
        this.name = name;
        this.desc = desc;
        this.asmType = type;
        this.isStatic = isStatic;
        this.signature = signature;
        this.type = type1;
    }

    public String internalClassName() {
        return internalClassName;
    }

    public String className() {
        return className;
    }

    public String name() {
        return name;
    }

    public String desc() {
        return desc;
    }

    public Type type() {
        return asmType;
    }

    public String signature() {
        return signature;
    }

    public boolean isStatic() {
        return isStatic;
    }

    private static final ReferenceInfo UNIMPLEMENTED = new ReferenceInfo(null, null, "<unimplemented>", null, null, false, RefType.UNIMPLEMENTED);

    /**
     * Returns the reference info object which is always determined
     * to be unimplemented by the {@link AbstractionProvider}.
     *
     * @return The unimplemented reference.
     */
    public static ReferenceInfo unimplemented() {
        return UNIMPLEMENTED;
    }

    public static ReferenceInfo forMethod(Method method) {
        String desc = Type.getMethodDescriptor(method);
        return new ReferenceInfo(method.getDeclaringClass().getName().replace('.', '/'),
                method.getDeclaringClass().getName(),
                method.getName(),
                desc,
                Type.getMethodType(desc),
                Modifier.isStatic(method.getModifiers()),
                RefType.METHOD);
    }

    public static ReferenceInfo forMethodInfo(String ownerName, String name, String desc, boolean isStatic) {
        return new ReferenceInfo(ownerName.replace('.', '/'), ownerName.replace('/', '.'),
                name, desc, Type.getMethodType(desc), isStatic, RefType.METHOD);
    }

    public static ReferenceInfo forMethodInfo(String ownerName, String name, String desc, String sig, boolean isStatic) {
        return new ReferenceInfo(ownerName.replace('.', '/'), ownerName.replace('/', '.'),
                name, desc, Type.getMethodType(desc), isStatic, sig, RefType.METHOD);
    }

    public static ReferenceInfo forMethodInfo(Class<?> klass, String name, boolean isStatic, Class<?> returnType, Class<?>... argTypes) {
        Type type = Type.getMethodType(Type.getType(returnType), ASMUtil.asTypes(argTypes));
        return new ReferenceInfo(klass.getName().replace('.', '/'), klass.getName(),
                name, type.getDescriptor(), type, isStatic, RefType.METHOD);
    }

    public static ReferenceInfo forFieldInfo(String ownerName, String name, String desc, boolean isStatic) {
        return new ReferenceInfo(ownerName.replace('.', '/'), ownerName.replace('/', '.'),
                name, desc, Type.getType(desc), isStatic, RefType.FIELD);
    }

    public static ReferenceInfo forFieldInfo(String ownerName, String name, String desc, String sig, boolean isStatic) {
        return new ReferenceInfo(ownerName.replace('.', '/'), ownerName.replace('/', '.'),
                name, desc, Type.getType(desc), isStatic, sig, RefType.FIELD);
    }

    public static ReferenceInfo forClass(String className) {
        String internalName = className.replace('.', '/');
        String desc = "L" + internalName + ";";
        return new ReferenceInfo(internalName, className.replace('/', '.'), null,
                desc, Type.getType(desc), false, null, RefType.CLASS);
    }

    public static ReferenceInfo forClass(Class<?> klass) {
        return forClass(klass.getName());
    }

    /**
     * Check whether this reference describes a field.
     *
     * @return Whether it describes a field.
     */
    public boolean isField() {
        return type == RefType.FIELD;
    }

    public RefType refType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReferenceInfo that = (ReferenceInfo) o;
        return Objects.equals(internalClassName, that.internalClassName) && Objects.equals(name, that.name) && Objects.equals(desc, that.desc) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        if (hashCode != 0)
            return hashCode;

        int hc = 1;
        hc = 31 * hc + Objects.hashCode(internalClassName);
        hc = 31 * hc + Objects.hashCode(name);
        hc = 31 * hc + Objects.hashCode(desc);
        hc = 31 * hc + Objects.hashCode(type);
        return this.hashCode = hc;
    }

    @Override
    public String toString() {
        if (isField()) return "field " + className + "." + name + ":" + desc;
        else return "method " + className + "." + name + desc;
    }
}
