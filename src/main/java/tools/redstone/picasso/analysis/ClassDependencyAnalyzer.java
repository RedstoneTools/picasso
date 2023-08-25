package tools.redstone.picasso.analysis;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import tools.redstone.picasso.AbstractionProvider;
import tools.redstone.picasso.usage.NotImplementedException;
import tools.redstone.picasso.usage.Usage;
import tools.redstone.picasso.util.asm.ASMUtil;
import tools.redstone.picasso.util.asm.ComputeStack;
import tools.redstone.picasso.util.data.CollectionUtil;
import tools.redstone.picasso.util.data.Container;
import tools.redstone.picasso.util.asm.MethodWriter;
import tools.redstone.picasso.util.ReflectUtil;
import tools.redstone.picasso.util.data.ExStack;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes given class bytes for usage of abstraction methods.
 *
 * @author orbyfied
 */
public class ClassDependencyAnalyzer {

    static final Set<String> specialMethods = Set.of("unimplemented", "isImplemented", "<init>");     // Special methods on abstractions

    // The result of the dependency analysis on a class
    public class ClassAnalysis {
        public boolean completed = false;                                                     // Whether this analysis is complete
        public boolean running = false;
        public final Map<ReferenceInfo, ReferenceAnalysis> analyzedMethods = new HashMap<>(); // All analysis objects for the methods in this class
        public List<Dependency> dependencies = new ArrayList<>();                             // All dependencies recorded in this class

        // Check whether all direct and switch dependencies are implemented
        public boolean areAllImplemented() {
            for (Dependency dep : dependencies)
                if (!dep.isImplemented(abstractionProvider))
                    return false;
            return true;
        }
    }

    /* Stack Tracking */
    /** Represents a lambda value made using invokedynamic */
    public record Lambda(boolean direct, ReferenceInfo methodInfo, Container<Boolean> discard) implements ComputeStack.Value {
        @Override
        public Type type() {
            return null; // todo
        }
    }

    static final Type TYPE_Usage = Type.getType(Usage.class);
    static final String NAME_Usage = TYPE_Usage.getInternalName();
    static final Type TYPE_InternalSubstituteMethods = Type.getType(Usage.InternalSubstituteMethods.class);
    static final String NAME_InternalSubstituteMethods = TYPE_InternalSubstituteMethods.getInternalName();
    static final Type TYPE_NotImplementedException = Type.getType(NotImplementedException.class);
    static final String NAME_NotImplementedException = TYPE_NotImplementedException.getInternalName();
    static final Type TYPE_MethodInfo = Type.getType(ReferenceInfo.class);
    static final String NAME_MethodInfo = TYPE_MethodInfo.getInternalName();

    private final AbstractionProvider abstractionProvider;                  // The abstraction manager
    private String internalName;                                          // The internal name of this class
    private String className;                                             // The public name of this class
    private ClassReader classReader;                                      // The class reader for the bytecode
    private ClassNode classNode;                                          // The class node to be written
    public final List<ClassAnalysisHook> hooks = new ArrayList<>();  // The analysis hooks

    private ClassAnalysis classAnalysis = new ClassAnalysis(); // The result of analysis

    public ClassDependencyAnalyzer addHook(ClassAnalysisHook hook) {
        this.hooks.add(hook);
        return this;
    }

    public ClassDependencyAnalyzer(AbstractionProvider manager,
                                   ClassReader classReader) {
        this.abstractionProvider = manager;
        if (classReader != null) {
            this.internalName = classReader.getClassName();
            this.className = internalName.replace('/', '.');
            this.classReader = classReader;
            this.classNode = new ClassNode(ASMUtil.ASM_V);
            classReader.accept(classNode, 0);
        }
    }

    // Make a ReferenceInfo to a method on the stack
    private static void makeMethodInfo(MethodVisitor visitor, String owner, String name, String desc, boolean isStatic) {
        visitor.visitLdcInsn(owner);
        visitor.visitLdcInsn(name);
        visitor.visitLdcInsn(desc);
        visitor.visitIntInsn(Opcodes.BIPUSH, isStatic ? 1 : 0);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE_MethodInfo.getInternalName(), "forMethodInfo",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)L" + NAME_MethodInfo + ";", false);
    }

    // Make a ReferenceInfo to a field on the stack
    private static void makeFieldInfo(MethodVisitor visitor, String owner, String name, String desc, boolean isStatic) {
        visitor.visitLdcInsn(owner);
        visitor.visitLdcInsn(name);
        visitor.visitLdcInsn(desc);
        visitor.visitIntInsn(Opcodes.BIPUSH, isStatic ? 1 : 0);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE_MethodInfo.getInternalName(), "forFieldInfo",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)L" + NAME_MethodInfo + ";", false);
    }

    /** Get a method analysis if present */
    public ReferenceAnalysis getReferenceAnalysis(ReferenceInfo info) {
        return abstractionProvider.getReferenceAnalysis(info);
    }

    /** Analyzes and transforms a local method */
    public ReferenceAnalysis localMethod(AnalysisContext context, ReferenceInfo info) {
        try {
            if (!info.internalClassName().equals(this.internalName))
                throw new AssertionError();

            // check for cached
            var analysis = getReferenceAnalysis(info);
            if (analysis != null && analysis.complete) {
                return analysis;
            }

            // check for recursion
            if (context.analysisStack.contains(info)) {
                return null;
            }

            // find method node
            MethodNode m = ASMUtil.findMethod(classNode, info.name(), info.desc());
            if (m == null) {
                // return partial
                // todo: try to find in super class or smth
                return abstractionProvider.makePartial(info);
            }

            // create analysis, visit method and register result
            analysis = new ReferenceAnalysis(this, info);
            MethodVisitor v = methodVisitor(context, info, analysis, m);
            m.accept(v); // the visitor registers the result automatically
            v.visitEnd();
            return analysis;
        } catch (Exception e) {
            throw new RuntimeException("Error while analyzing local method " + info, e);
        }
    }

    /** Analyzes and transforms a method from any class */
    public ReferenceAnalysis publicReference(AnalysisContext context, ReferenceInfo info) {
        // check for local method
        if (info.internalClassName().equals(this.internalName) && !info.isField())
            return localMethod(context, info);
        return abstractionProvider.publicReference(context, info);
    }

    /** Check whether the given reference could be a dependency */
    public boolean isDependencyReference(AnalysisContext context, ReferenceInfo info) {
        for (var hook : this.hooks) {
            var res = hook.isDependencyCandidate(context, info);
            if (res == null) continue;
            return res;
        }

        // assume no
        return false;
    }

    /**
     * Creates a method visitor which analyzes and transforms a local method.
     *
     * @param currentMethodInfo The method descriptor.
     * @return The visitor.
     */
    public MethodVisitor methodVisitor(AnalysisContext context, ReferenceInfo currentMethodInfo, ReferenceAnalysis currentMethodAnalysis, MethodNode oldMethod) {
        String name = currentMethodInfo.name();
        String descriptor = currentMethodInfo.desc();

        // check old method
        if (oldMethod == null) {
            throw new IllegalArgumentException("No local method by " + currentMethodInfo + " in class " + internalName);
        }

        abstractionProvider.registerAnalysis(currentMethodAnalysis);
        classAnalysis.analyzedMethods.put(currentMethodInfo, currentMethodAnalysis);

        // Tries to estimate/track the current compute stack
        final ComputeStack computeStack = new ComputeStack();

        context.enteredMethod(currentMethodInfo, computeStack);
        for (var hook : hooks) hook.enterMethod(context);

        // create new method
        boolean isThisMethodStatic = Modifier.isStatic(oldMethod.access);
        MethodNode newMethod = new MethodNode(oldMethod.access, name, descriptor, oldMethod.signature, oldMethod.exceptions.toArray(new String[0]));

        // create visitor hooks
        var mWriter = new MethodWriter(newMethod, newMethod);
        final List<ClassAnalysisHook.MethodVisitorHook> methodVisitorHooks = new ArrayList<>();
        for (var hook : hooks)
            CollectionUtil.addIfNotNull(methodVisitorHooks, hook.visitMethod(context, mWriter));

        // create method visitor
        var visitor = new ComputeStack.TrackingMethodVisitor<MethodWriter>(
                new MethodWriter(newMethod, newMethod),
                computeStack,
                oldMethod,
                currentMethodInfo
        ) {
            boolean endVisited = false;

            public void addInsn(InsnNode node) {
                newMethod.instructions.add(node);
            }

            @Override
            public void visitCode() {

            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                // check for lambda factory
                if (
                        !bootstrapMethodHandle.getOwner().equals("java/lang/invoke/LambdaMetafactory") ||
                                !bootstrapMethodHandle.getName().equals("metafactory")
                ) {
                    super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                    return;
                }

                // check whether its a lambda or a
                // method referenced as a lambda argument
                Handle lambdaImpl = (Handle) bootstrapMethodArguments[1];
                Type lambdaImplType = Type.getMethodType(lambdaImpl.getDesc());
                int argCount = lambdaImplType.getArgumentTypes().length +
                        (lambdaImpl.getTag() != Opcodes.H_INVOKESTATIC && lambdaImpl.getTag() != Opcodes.H_GETSTATIC ? 1 : 0);
                boolean isDirect = !lambdaImpl.getName().startsWith("lambda$");
                var lambda = new Lambda(isDirect, new ReferenceInfo(
                        lambdaImpl.getOwner(),
                        lambdaImpl.getOwner().replace('/', '.'), lambdaImpl.getName(),
                        lambdaImpl.getDesc(), Type.getMethodType(lambdaImpl.getDesc()),
                        lambdaImpl.getTag() == Opcodes.H_INVOKESTATIC,
                        RefType.METHOD), new Container<>(false));

                addInsn(new InsnNode(-1) {
                    @Override
                    public void accept(MethodVisitor methodVisitor) {
                        // check if it should be discarded
                        if (lambda.discard.value) {
                            for (int i = 0; i < argCount; i++)
                                methodVisitor.visitInsn(Opcodes.POP);
                            methodVisitor.visitInsn(Opcodes.ACONST_NULL);
                        } else {
                            methodVisitor.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                        }
                    }
                });

                for (int i = 0; i < argCount; i++)
                    computeStack.pop();
                computeStack.push(lambda);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                final ReferenceInfo calledMethodInfo = ReferenceInfo.forMethodInfo(owner, name, descriptor, /* todo: find generic sig */ descriptor, opcode == Opcodes.INVOKESTATIC);

                /* Check for hook intercepts */
                for (var vh : methodVisitorHooks) {
                    if (vh.visitMethodInsn(context, opcode, calledMethodInfo)) {
                        return;
                    }
                }

                /* Check for usage of dependencies through proxy methods */

                // check for Usage.optionally(Supplier<T>)
                if (NAME_Usage.equals(owner) && "optionally".equals(name)) {
                    var lambda = (Lambda) computeStack.pop();
                    ReferenceAnalysis analysis;
                    analysis = publicReference(context, lambda.methodInfo);
                    analysis.referenceOptional(context);

                    List<ReferenceInfo> dependencies = lambda.direct() ?
                            List.of(lambda.methodInfo()) :
                            analysis.requiredDependencies;
                    if (dependencies != null) {
                        dependencies.forEach(dep ->
                                classAnalysis.dependencies.add(new ReferenceDependency(true, dep, null)));
                    }

                    // discard lambda if the dependencies arent fulfilled
                    boolean allImplemented = abstractionProvider.areAllImplemented(dependencies);
                    if (!allImplemented) {
                        if (!lambda.direct()) {
                            analysis.optionalReferenceDropped(context);
                        }

                        lambda.discard.value = true;
                    }

                    if ("(Ljava/util/function/Supplier;)Ljava/util/Optional;".equals(descriptor)) {
                        // transform bytecode
                        if (!allImplemented) {
                            // the methods are not all implemented,
                            // substitute call with notPresentOptional
                            parent.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    NAME_InternalSubstituteMethods, "notPresentOptional",
                                    "(Ljava/util/function/Supplier;)Ljava/util/Optional;", false
                            );
                        } else {
                            // the methods are implemented, dont substitute
                            parent.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }

                        computeStack.push(ComputeStack.ReturnValue.of(calledMethodInfo));
                        return;
                    }

                    if ("(Ljava/lang/Runnable;)B".equals(descriptor)) {
                        // transform bytecode
                        if (!allImplemented) {
                            // the methods are not all implemented,
                            // substitute call with notPresentBoolean
                            parent.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    NAME_InternalSubstituteMethods, "notPresentBoolean",
                                    "(Ljava/lang/Runnable;)B", false
                            );
                        } else {
                            // the methods are implemented, dont substitute
                            parent.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }

                        computeStack.push(ComputeStack.ReturnValue.of(calledMethodInfo));
                        return;
                    }

                    return;
                }

                // check for Usage.oneOf(Optional<T>...)
                if (NAME_Usage.equals(owner) && "either".equals(name) && "([Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(descriptor)) {
                    // get array of lambdas
                    Lambda[] lambdas = ReflectUtil.arrayCast(computeStack.expectAndPop(ComputeStack.Array.class).array(), Lambda.class);
                    Lambda chosen = null;                                            // The chosen lambda
                    List<ReferenceDependency> chosenDependencies = new ArrayList<>();   // The method dependencies of the chosen lambda
                    List<ReferenceDependency> optionalDependencies = new ArrayList<>(); // The optional dependencies of this switch
                    int i = 0;
                    for (int n = lambdas.length; i < n; i++) {
                        Lambda lambda = lambdas[i];
                        ReferenceAnalysis analysis;
                        analysis = publicReference(context, lambda.methodInfo());

                        // get dependencies as methods
                        List<ReferenceInfo> dependencies = lambda.direct() ?
                                List.of(lambda.methodInfo()) :
                                analysis.requiredDependencies;

                        // if not implemented, add as optional dependencies
                        if (!abstractionProvider.areAllImplemented(dependencies) || chosen != null) {
                            CollectionUtil.mapImmediate(dependencies, dep -> new ReferenceDependency(true, dep, null), classAnalysis.dependencies, optionalDependencies);
                            lambda.discard.value = true;
                            continue;
                        }

                        // if one is implemented, add as required dependencies
                        chosen = lambda;
                        currentMethodAnalysis.registerReference(lambda.methodInfo); // register lambda as referenced by the current method
                        analysis.referenceRequired(context);
                        if (dependencies != null) {
                            CollectionUtil.mapImmediate(dependencies, dep -> new ReferenceDependency(false, dep, false), classAnalysis.dependencies, chosenDependencies);
                        }

                        break;
                    }

                    // register switch
                    classAnalysis.dependencies.add(new RequireOneDependency(chosenDependencies, optionalDependencies, chosen != null));

                    // replace method call
                    if (chosen != null) {
                        // push index into supplier array
                        parent.visitIntInsn(Opcodes.SIPUSH, i);

                        parent.visitMethodInsn(Opcodes.INVOKESTATIC, NAME_InternalSubstituteMethods,
                                "onePresent", "([Ljava/util/function/Supplier;I)Ljava/lang/Object;",
                                false);
                    } else {
                        // add unimplemented dependency
                        currentMethodAnalysis.registerReference(ReferenceInfo.unimplemented());

                        parent.visitMethodInsn(Opcodes.INVOKESTATIC, NAME_InternalSubstituteMethods,
                                "nonePresent", "([Ljava/util/function/Supplier;)Ljava/lang/Object;",
                                false);
                    }

                    return;
                }

                // analyze public method
                var analysis = publicReference(context, calledMethodInfo);
                if (analysis != null) {
                    currentMethodAnalysis.requiredDependencies.addAll(analysis.requiredDependencies);
                    currentMethodAnalysis.allAnalyzedReferences.add(analysis);
                }

                /* Check for direct usage of dependencies */
                if (isDependencyReference(context, calledMethodInfo) && !specialMethods.contains(name)) {
                    classAnalysis.dependencies.add(new ReferenceDependency(false, calledMethodInfo, null));

                    // dont transform required if the method its called in
                    // is a block used by Usage.optionally
                    if (currentMethodAnalysis.optionalReferenceNumber <= 0) {
                        // insert runtime throw
                        if (!abstractionProvider.isImplemented(calledMethodInfo)) {
                            addInsn(new InsnNode(-1) {
                                @Override
                                public void accept(MethodVisitor mv) {
                                    if (currentMethodAnalysis.optionalReferenceNumber < 0) {
                                        mv.visitTypeInsn(Opcodes.NEW, NAME_NotImplementedException);
                                        mv.visitInsn(Opcodes.DUP);
                                        makeMethodInfo(mv, calledMethodInfo.internalClassName(), calledMethodInfo.name(), calledMethodInfo.desc(), calledMethodInfo.isStatic());
                                        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, NAME_NotImplementedException, "<init>", "(L" + NAME_MethodInfo + ";)V", false);
                                        mv.visitInsn(Opcodes.ATHROW);
                                    }
                                }
                            });
                        }
                    }

                    currentMethodAnalysis.requiredDependencies.add(calledMethodInfo);
                }

                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                Type type1 = Type.getObjectType(type);
                /* Visit hooks */
                for (var vh : methodVisitorHooks) {
                    if (vh.visitTypeInsn(context, opcode, type1)) {
                        return;
                    }
                }

                super.visitTypeInsn(opcode, type);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                /* Visit hooks */
                var fieldNode = owner.equals(internalName) ? ASMUtil.findField(classNode, name) : null;
                var fieldInfo = ReferenceInfo.forFieldInfo(owner, name, descriptor,
                        fieldNode != null ? fieldNode.signature : null, opcode == Opcodes.GETSTATIC);
                for (var vh : methodVisitorHooks) {
                    if (vh.visitFieldInsn(context, opcode, fieldInfo)) {
                        return;
                    }
                }

                if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
                    // pop instance if necessary
                    if (opcode == Opcodes.GETFIELD) {
                        computeStack.pop();
                    }

                    computeStack.push(new ComputeStack.FieldValue(fieldInfo));

                    // register reference
                    var analysis = publicReference(context, fieldInfo);
                    context.currentAnalysis().registerReference(analysis);
                    analysis.referenceRequired(context);

                    /* Check for direct usage of dependencies */
                    if (isDependencyReference(context, fieldInfo) && !specialMethods.contains(name)) {
                        classAnalysis.dependencies.add(new ReferenceDependency(false, fieldInfo, null));

                        // dont transform required if the method its called in
                        // is a block used by Usage.optionally
                        if (currentMethodAnalysis.optionalReferenceNumber <= 0) {
                            // insert runtime throw
                            if (!abstractionProvider.isImplemented(fieldInfo)) {
                                addInsn(new InsnNode(-1) {
                                    @Override
                                    public void accept(MethodVisitor mv) {
                                        if (currentMethodAnalysis.optionalReferenceNumber < 0) {
                                            mv.visitTypeInsn(Opcodes.NEW, NAME_NotImplementedException);
                                            mv.visitInsn(Opcodes.DUP);
                                            makeFieldInfo(mv, fieldInfo.internalClassName(), fieldInfo.name(), fieldInfo.desc(), fieldInfo.isStatic());
                                            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, NAME_NotImplementedException, "<init>", "(L" + NAME_MethodInfo + ";)V", false);
                                            mv.visitInsn(Opcodes.ATHROW);
                                        }
                                    }
                                });
                            }
                        }

                        currentMethodAnalysis.requiredDependencies.add(fieldInfo);
                    }

                    parent.visitFieldInsn(opcode, owner, name, descriptor);
                    return;
                }

                super.visitFieldInsn(opcode, owner, name, descriptor);
            }

            @Override
            public void visitVarInsn(int opcode, int varIndex) {
                /* Visit hooks */
                Type t = Type.getType(oldMethod.localVariables.get(varIndex).desc);
                String sig = oldMethod.localVariables.get(varIndex).signature;
                for (var vh : methodVisitorHooks) {
                    if (vh.visitVarInsn(context, opcode, varIndex, t, sig)) {
                        return;
                    }
                }

                super.visitVarInsn(opcode, varIndex);
            }

            @Override
            public void visitInsn(int opcode) {
                /* Visit hooks */
                for (var vh : methodVisitorHooks) {
                    if (vh.visitInsn(context, opcode)) {
                        return;
                    }
                }

                super.visitInsn(opcode);
            }

            @Override
            public void visitEnd() {
                if (endVisited) return;
                endVisited = true;

                for (var hook : methodVisitorHooks) hook.visitEnd();
                for (var hook : hooks) hook.leaveMethod(context);
                context.leaveMethod();
                currentMethodAnalysis.complete = true;

                classNode.methods.set(classNode.methods.indexOf(oldMethod), newMethod);
            }
        };

        return visitor;
    }

    /**
     * Analyze the class to locate and analyze it's dependencies, and set
     * the result to be retrievable by {@link #getClassAnalysis()}.
     *
     * @return This.
     */
    public ClassDependencyAnalyzer analyzeAndTransform() {
        if (classAnalysis.running)
            return this;

        /* find dependencies */
        classAnalysis.running = true;
        classNode.accept(new ClassVisitor(ASMUtil.ASM_V) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                ReferenceInfo info = new ReferenceInfo(internalName, className, name, descriptor, Type.getMethodType(descriptor), Modifier.isStatic(access), RefType.METHOD);

                // check for cached
                var analysis = getReferenceAnalysis(info);
                if (analysis != null && analysis.complete)
                    return null;

                // create analysis, visit method and register result
                return methodVisitor(new AnalysisContext(abstractionProvider), info, new ReferenceAnalysis(ClassDependencyAnalyzer.this, info), ASMUtil.findMethod(classNode, name, descriptor));
            }

            @Override
            public void visitEnd() {
                final AnalysisContext postAnalyzeCtx = new AnalysisContext(abstractionProvider);

                // post-analyze all methods
                for (MethodNode methodNode : classNode.methods) {
                    ReferenceAnalysis analysis = publicReference(postAnalyzeCtx, ReferenceInfo.forMethodInfo(internalName, methodNode.name, methodNode.desc, Modifier.isStatic(methodNode.access)));
                    if (analysis.optionalReferenceNumber < 0 || abstractionProvider.getRequiredMethodPredicate().test(analysis)) {
                        analysis.referenceRequired(postAnalyzeCtx);
                    }

                    analysis.postAnalyze();
                }

                // filter dependencies
                classAnalysis.dependencies = classAnalysis.dependencies.stream()
                        .map(d1 -> d1 instanceof ReferenceDependency d ? (d.optional() ? d : d.asOptional(publicReference(postAnalyzeCtx, d.info()).optionalReferenceNumber >= 0)) : d1)
                        .collect(Collectors.toList());

                // reduce dependencies
                Set<Dependency> finalDependencySet = new HashSet<>();
                for (Dependency dep : classAnalysis.dependencies) {
                    if (dep instanceof ReferenceDependency dependency) {
                        final ReferenceDependency mirror = dependency.asOptional(!dependency.optional());

                        if (!dependency.optional()) {
                            finalDependencySet.remove(mirror);
                            finalDependencySet.add(dependency);
                            continue;
                        }

                        if (!finalDependencySet.contains(mirror)) {
                            finalDependencySet.add(dependency);
                            continue;
                        }
                    } else {
                        finalDependencySet.add(dep);
                    }
                }

                classAnalysis.dependencies = new ArrayList<>(finalDependencySet);

                // mark complete
                classAnalysis.completed = true;
            }
        });

        return this;
    }

    public ClassAnalysis getClassAnalysis() {
        return classAnalysis;
    }

    public ClassNode getClassNode() {
        return classNode;
    }
}
