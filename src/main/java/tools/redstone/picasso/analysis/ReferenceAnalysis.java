package tools.redstone.picasso.analysis;

import org.objectweb.asm.tree.ClassNode;

import java.util.*;
import java.util.function.Supplier;

import static tools.redstone.picasso.util.data.CollectionUtil.addIfNotNull;

/**
 * Stores data about a reference. If not partial, this will also include
 * data gathered from analyzing the bytecode of the method.
 */
public class ReferenceAnalysis {
    public final ClassDependencyAnalyzer analyzer;                            // The analyzer instance.
    public final ReferenceInfo ref;                                           // The reference this analysis covers
    public List<ReferenceInfo> requiredDependencies = new ArrayList<>();      // All recorded required dependencies used by this method
    public int optionalReferenceNumber = 0;                                   // Whether this method is referenced in an optionally() block
    public Set<ReferenceAnalysis> allAnalyzedReferences = new HashSet<>();    // The analysis objects of all methods/fields normally called by this method
    public boolean complete = false;                                          // Whether this analysis has completed all mandatory tasks
    public boolean partial = false;                                           // Whether this analysis is used purely to store meta or if it is actually analyzed with bytecode analysis
    public final boolean field;                                               // Whether this references a field
    private Map<Object, Object> extra;                                        // Extra data which can be used by hooks

    public List<ClassAnalysisHook.ReferenceHook> refHooks = new ArrayList<>();

    public ReferenceAnalysis(ClassDependencyAnalyzer analyzer, ReferenceInfo ref) {
        this.analyzer = analyzer;
        this.ref = ref;
        this.field = ref.isField();
    }

    public ClassNode classNode() {
        return analyzer.getClassNode();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Object key) {
        if (extra == null)
            return null;
        return (T) extra.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Object def) {
        if (extra == null)
            return null;
        return (T) extra.getOrDefault(key, def);
    }

    public boolean has(Object key) {
        if (extra == null)
            return false;
        return extra.containsKey(key);
    }

    public void set(Object key, Object val) {
        if (extra == null)
            extra = new HashMap<>();
        extra.put(key, val);
    }

    public Map<Object, Object> extra() {
        return Collections.unmodifiableMap(extra);
    }

    // Checked refHooks.add
    private void addRefHook(ClassAnalysisHook hook, Supplier<ClassAnalysisHook.ReferenceHook> supplier) {
        // create new ref hook
        addIfNotNull(refHooks, supplier.get());
    }

    // Register and propagate that this method is part of an optional block
    public void referenceOptional(AnalysisContext context) {
        for (var hook : analyzer.hooks) addRefHook(hook, () -> hook.optionalReference(context, this));
        for (var refHook : refHooks) refHook.optionalReference(context);

        this.optionalReferenceNumber += 2;
        for (ReferenceAnalysis analysis : allAnalyzedReferences) {
            analysis.referenceOptional(context);
        }
    }

    // Register and propagate that this method is required
    public void referenceRequired(AnalysisContext context) {
        for (var hook : analyzer.hooks) addRefHook(hook, () -> hook.requiredReference(context, this));
        for (var refHook : refHooks) refHook.requiredReference(context);

        this.optionalReferenceNumber -= 1;
        for (ReferenceAnalysis analysis : allAnalyzedReferences) {
            analysis.referenceRequired(context);
        }
    }

    // Register and propagate that this method was dropped from an optionally() block
    public void optionalReferenceDropped(AnalysisContext context) {
        for (var refHook : refHooks) refHook.optionalBlockDiscarded(context);
        for (ReferenceAnalysis analysis : allAnalyzedReferences) {
            analysis.optionalReferenceDropped(context);
        }
    }

    // Finish analysis of the method
    public void postAnalyze() {
        for (var refHook : refHooks) refHook.postAnalyze();
        for (ReferenceAnalysis analysis : allAnalyzedReferences) {
            analysis.postAnalyze();
        }
    }

    public void registerReference(ReferenceInfo info) {
        addIfNotNull(allAnalyzedReferences, analyzer.getReferenceAnalysis(info));
    }

    public void registerReference(ReferenceAnalysis analysis) {
        allAnalyzedReferences.add(analysis);
    }

    public boolean isPartial() {
        return partial;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isField() {
        return field;
    }
}
