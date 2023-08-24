package tools.redstone.picasso.analysis;

import tools.redstone.picasso.AbstractionProvider;

/**
 * Represents the dependency of a piece of code on a specific method.
 */
public record ReferenceDependency(
        boolean optional,
        ReferenceInfo info,
        Boolean implemented
) implements Dependency {
    public ReferenceDependency asOptional(boolean optional) {
        return new ReferenceDependency(optional, info, implemented);
    }

    @Override
    public boolean isImplemented(AbstractionProvider abstractionProvider) {
        if (implemented != null)
            return implemented;
        return abstractionProvider.isImplemented(info);
    }

    public String toString() {
        return "RefDependency(" + (optional ? "optional " : "required ") +
                info.className() + "." + info.name() +
                ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReferenceDependency that = (ReferenceDependency) o;
        return info.equals(that.info) && implemented == that.implemented;
    }

    @Override
    public int hashCode() {
        return info.hashCode() + 31 * (implemented == Boolean.TRUE ? 1 : 0);
    }
}
