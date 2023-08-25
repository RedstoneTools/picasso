package tools.redstone.picasso.usage;

import java.util.function.Supplier;

/**
 * Signifies that a dependency switch, such as {@link Usage#either(Supplier[])},
 * failed to find an implementation which had the required dependencies and
 * therefore cannot execute.
 */
public class NoneImplementedException extends RuntimeException {

    public NoneImplementedException(String message) {
        super(message);
    }

}
