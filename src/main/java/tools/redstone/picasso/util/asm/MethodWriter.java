package tools.redstone.picasso.util.asm;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.function.Consumer;

public class MethodWriter extends MethodVisitor {

    private final MethodNode newMethodNode;
    private final MethodVisitor visitor;

    public MethodWriter(MethodNode node, MethodVisitor visitor) {
        super(ASMUtil.ASM_V, visitor);
        this.newMethodNode = node;
        this.visitor = visitor;
    }

    public void addInsn(AbstractInsnNode node) {
        this.newMethodNode.instructions.add(node);
    }

    public void addInsn(Consumer<MethodVisitor> writer) {
        addInsn(new InsnNode(-1) {
            @Override
            public void accept(MethodVisitor methodVisitor) {
                writer.accept(methodVisitor);
            }
        });
    }

}
