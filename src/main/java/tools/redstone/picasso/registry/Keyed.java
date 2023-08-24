package tools.redstone.picasso.registry;

/**
 * Represents a value which directly contains a key.
 */
public interface Keyed<K> {
    K getKey();
}
