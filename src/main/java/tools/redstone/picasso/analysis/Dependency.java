package tools.redstone.picasso.analysis;

import tools.redstone.picasso.AbstractionProvider;

public interface Dependency {

    /** Check whether this dependency is fully implemented */
    boolean isImplemented(AbstractionProvider manager);

}
