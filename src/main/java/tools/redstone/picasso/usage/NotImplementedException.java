package tools.redstone.picasso.usage;

import tools.redstone.picasso.analysis.ReferenceInfo;

public class NotImplementedException extends RuntimeException {

    private final ReferenceInfo ref; // The reference that wasn't implemented

    public NotImplementedException() {
        this(null);
    }

    public NotImplementedException(ReferenceInfo info) {
        this.ref = info;
    }

    public ReferenceInfo getReference() {
        return ref;
    }

    @Override
    public String getMessage() {
        if (ref == null) return null;
        return ref + " is not implemented";
    }

}
