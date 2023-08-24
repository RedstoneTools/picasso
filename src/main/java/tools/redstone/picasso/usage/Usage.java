package tools.redstone.picasso.usage;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Methods for usage of dependencies.
 *
 * Recommended to statically import this class using
 * ```
 * import static tools.redstone.abstracraft.usage.Usage.*;
 * ```
 * So the methods can be used similar to keywords, like:
 * ```
 * optionally(() -> abstraction.myMethod())
 * either(() -> abstraction.a(), abstraction::b)
 * ```
 *
 * Reference the javadocs provided per method if you're unsure
 * about specific workings or usage.
 *
 * @author orbyfied
 */
public class Usage {

    /**
     * NOTE: This directive is transformed by the bytecode analyzer, this is substituted
     * with another method if the dependencies are unavailable.
     *
     * Signifies an optional usage of the dependencies in the given block.
     * Will return an optional containing the return value if all dependencies required by
     * the block are implemented, otherwise returns {@code Optional.empty()}.
     *
     * Pseudo-code:
     * ```
     * return isImplemented(block) ? Optional.of(block.execute()) : Optional.empty()
     * ```
     *
     * @param block The code block (in the form of a supplier).
     * @param <T> The returned value type.
     * @return The optional containing the result if implemented
     */
    public static <T> Optional<T> optionally(Supplier<T> block) {
        return Optional.of(block.get());
    }

    /**
     * NOTE: This directive is transformed by the bytecode analyzer, this is substituted
     * with another method if the dependencies are unavailable.
     *
     * Signifies an optional usage of the dependencies in the given block.
     * Will return true if all dependencies required by the block are implemented so the
     * block is executed, otherwise returns {@code false}.
     *
     * Pseudo-code:
     * ```
     * return isImplemented(block) ? { block.run(); return true; } : false
     * ```
     *
     * @param block The code block (in the form of a supplier).
     * @return Whether the block executed successfully.
     */
    public static boolean optionally(Runnable block) {
        block.run();
        return true;
    }

    /**
     * NOTE: This directive is transformed by the bytecode analyzer, this is substituted
     * with another method chosen by the dependencies available.
     *
     * Signifies required usage of at least one of the blocks provided,
     * returns the return value of the first supplier with all dependencies implemented
     * if one is present, otherwise it will throw an {@link NoneImplementedException}.
     *
     * Pseudo-code:
     * ```
     * return findFirstImplementedBlock() is present ? block.execute() : throw NoneImplementedException
     * ``
     *
     * @param blocks The blocks to choose from.
     * @param <T> The value return type.
     * @throws NoneImplementedException If none of the blocks have all their required dependencies implemented.
     * @return The return value of the first implemented block.
     */
    @SafeVarargs
    public static <T> T either(Supplier<T>... blocks) {
        throw new AssertionError(); // THIS WILL BE SUBSTITUTED BY THE BYTECODE TRANSFORMER
    }

    /**
     * Substitute methods that should only be called by code written
     * through the bytecode transformer.
     */
    public static class InternalSubstituteMethods {
        // Substitute for `optionally(Supplier<T>)` when it is not present
        public static Optional<?> notPresentOptional(Supplier<?> supplier) {
            return Optional.empty();
        }

        // Substitute for `optionally(Runnable)` when it is not present
        public static boolean notPresentBoolean(Runnable r) {
            return false;
        }

        // Substitute for `either(Supplier<T>...)` when at least one is present
        public static Object onePresent(Supplier<?>[] suppliers, int supplierIndex) {
            return suppliers[supplierIndex].get();
        }

        // Substitute for `either(Supplier<T>...)` when none are present
        public static Object nonePresent(Supplier<?>... suppliers) {
            throw new NoneImplementedException("");
        }
    }

}
