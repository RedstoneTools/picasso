package tools.redstone.picasso.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The result of the dependency analysis on a class
public class ClassAnalysis {
    public final ClassDependencyAnalyzer analyzer;
    public boolean completed = false;                                                     // Whether this analysis is complete
    public boolean running = false;
    public final Map<ReferenceInfo, ReferenceAnalysis> analyzedMethods = new HashMap<>(); // All analysis objects for the methods in this class
    public List<Dependency> dependencies = new ArrayList<>();                             // All dependencies recorded in this class

    public ClassAnalysis(ClassDependencyAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    // Check whether all direct and switch dependencies are implemented
    public boolean areAllImplemented() {
        for (Dependency dep : dependencies)
            if (!dep.isImplemented(analyzer.abstractionProvider))
                return false;
        return true;
    }
}
