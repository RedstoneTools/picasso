package tools.redstone.picasso.analysis;

import tools.redstone.picasso.AbstractionProvider;

import java.util.List;
import java.util.function.Supplier;

/**
 * Records the result of a dependency switch, such as
 * {@link tools.redstone.picasso.usage.Usage#either(Supplier[])}
 * which has a set of required dependencies and a set of
 * optional ones.
 */
public record SwitchDependency(List<ReferenceDependency> dependencies, List<ReferenceDependency> optionalDependencies, boolean implemented) implements Dependency {
    @Override
    public boolean isImplemented(AbstractionProvider manager) {
        return this.implemented;
    }

    @Override
    public boolean equals(Object o) {
        return this == o; // there shouldn't be any duplicates so this isnt important
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this); // there shouldn't be any duplicates so this isnt important
    }
}
