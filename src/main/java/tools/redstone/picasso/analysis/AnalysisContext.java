package tools.redstone.picasso.analysis;

import tools.redstone.picasso.AbstractionProvider;
import tools.redstone.picasso.util.asm.ComputeStack;

import java.io.PrintStream;
import java.util.Stack;

public class AnalysisContext {

    /**
     * The abstraction manager.
     */
    private final AbstractionProvider abstractionProvider;

    /**
     * The trace of the methods being analyzed.
     */
    public final Stack<ReferenceInfo> analysisStack = new Stack<>();

    // The current compute stacks from the methods.
    final Stack<ComputeStack> computeStacks = new Stack<>();

    public AnalysisContext(AbstractionProvider abstractionProvider) {
        this.abstractionProvider = abstractionProvider;
    }

    // Leaves a method and updates the context to account for it
    protected void leaveMethod() {
        analysisStack.pop();
        computeStacks.pop();
    }

    // Updates the context when entering a method, assumes shits already on the stacks.
    protected void enteredMethod(ReferenceInfo info,
                       ComputeStack computeStack) {
        analysisStack.push(info);
        computeStacks.push(computeStack);
    }

    // For debugging purposes
    public void printAnalysisTrace(PrintStream stream) {
        for (int i = analysisStack.size() - 1; i >= 0; i--) {
            var ref = analysisStack.get(i);
            stream.println(" " + (i == analysisStack.size() - 1 ? "-> " : " - ") + ref);
        }
    }

    public AbstractionProvider abstractionProvider() {
        return abstractionProvider;
    }

    /**
     * Get what method is currently being analyzed.
     *
     * @return The reference info.
     */
    public ReferenceInfo currentMethod() {
        if (analysisStack.isEmpty())
            return null;
        return analysisStack.peek();
    }

    /**
     * Get the reference analysis of the method that
     * is currently being analyzed.
     *
     * @return The analysis.
     */
    public ReferenceAnalysis currentAnalysis() {
        var curr = currentMethod();
        if (curr == null)
            return null;
        return abstractionProvider.getReferenceAnalysis(curr);
    }

    /** Gets the current compute stack */
    public ComputeStack currentComputeStack() {
        if (computeStacks.isEmpty())
            return null;
        return computeStacks.peek();
    }

    /** Get a clone of the current compute stack */
    public ComputeStack cloneComputeStack() {
        try {
            return (ComputeStack) currentComputeStack().clone();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

}
