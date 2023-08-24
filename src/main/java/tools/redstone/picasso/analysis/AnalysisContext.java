package tools.redstone.picasso.analysis;

import tools.redstone.picasso.AbstractionProvider;
import tools.redstone.picasso.util.data.ExStack;

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
    final Stack<ExStack<Object>> computeStacks = new Stack<>();

    public AnalysisContext(AbstractionProvider abstractionProvider) {
        this.abstractionProvider = abstractionProvider;
    }

    // Leaves a method and updates the context to account for it
    void leaveMethod() {
        analysisStack.pop();
        computeStacks.pop();
    }

    // Updates the context when entering a method, assumes shits already on the stacks.
    void enteredMethod(ReferenceInfo info,
                       ExStack<Object> computeStack) {
        analysisStack.push(info);
        computeStacks.push(computeStack);
    }

    void printAnalysisTrace(PrintStream stream) {
        for (int i = analysisStack.size() - 1; i >= 0; i--) {
            var ref = analysisStack.get(i);
            stream.println(" " + (i == analysisStack.size() - 1 ? "-> " : " - ") + ref);
        }
    }

    public AbstractionProvider abstractionProvider() {
        return abstractionProvider;
    }

    public ReferenceInfo currentMethod() {
        if (analysisStack.isEmpty())
            return null;
        return analysisStack.peek();
    }

    public ReferenceAnalysis currentAnalysis() {
        var curr = currentMethod();
        if (curr == null)
            return null;
        return abstractionProvider.getReferenceAnalysis(curr);
    }

    /** Gets the current compute stack */
    public ExStack<Object> currentComputeStack() {
        if (computeStacks.isEmpty())
            return null;
        return computeStacks.peek();
    }

    /** Get a clone of the current compute stack */
    @SuppressWarnings("unchecked")
    public ExStack<Object> cloneComputeStack() {
        try {
            return (ExStack<Object>) currentComputeStack().clone();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

}
