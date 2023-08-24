package tools.redstone.picasso.registry;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A bi-directional map-like structure for keys and
 * values of the given types.
 *
 * @param <K> The key type.
 * @param <V> The value type.
 */
public interface Registry<K, V> extends Iterable<V> {

    record Entry<K, V>(K key, V value) { }

    /**
     * Get the base class of the value type.
     *
     * @return The class.
     */
    Class<? super V> valueClass();

    /**
     * Get the current amount of elements in this registry.
     *
     * @return The amount of elements.
     */
    int size();

    /**
     * Get a value by the given key in this registry
     * if present, if absent it returns an empty optional.
     *
     * @param key The key.
     * @return The optional containing the value if present.
     */
    Optional<V> get(K key);

    /**
     * Get a value by the given key.
     *
     * @param key The key.
     * @return The value or null if absent.
     */
    default V getOrNull(K key) {
        return get(key).orElse(null);
    }

    /**
     * Get the key for the given value.
     *
     * @param val The value to get the key for.
     * @return The key.
     */
    K keyOf(V val);

    /**
     * Register the given value by the given key.
     *
     * @param key The key.
     * @param val The value.
     */
    void put(K key, V val);

    /**
     * Register the given value, retrieving the key
     * from {@link #keyOf(Object)}.
     *
     * @param val The value.
     */
    default void register(V val) {
        put(keyOf(val), val);
    }

    /**
     * Get a collection of all registered keys in this registry.
     *
     * @return The keys.
     */
    Collection<K> keys();

    /**
     * Get a collection of all registered values in this registry.
     *
     * @return The values.
     */
    Collection<V> values();

    /**
     * Get a stream of all entries in this registry.
     *
     * @return The stream of entries.
     */
    Stream<Entry<K, V>> entries();

    /**
     * Get a stream of all values in this registry.
     *
     * @return The stream of values.
     */
    default Stream<V> stream() {
        return values().stream();
    }

    @Override
    default Iterator<V> iterator() {
        return values().iterator();
    }

}
