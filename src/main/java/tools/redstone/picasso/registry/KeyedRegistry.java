package tools.redstone.picasso.registry;

import java.util.*;
import java.util.stream.Stream;

/**
 * A registry which contains {@link Keyed} values.
 *
 * It only maps keys to values and uses the {@link Keyed} property
 * of the values to get the keys.
 */
public class KeyedRegistry<K, V extends Keyed<K>> implements Registry<K, V> {
    protected final Class<? super V> vClass; // The runtime value class
    protected final Map<K, V> map;           // The map mapping the keys to the values

    public KeyedRegistry(Class<? super V> vClass, Map<K, V> map) {
        this.vClass = vClass;
        this.map = map;
    }

    public static <K, V extends Keyed<K>> KeyedRegistry<K, V> ordered(Class<? super V> vClass) {
        return new KeyedRegistry<>(vClass, new LinkedHashMap<>());
    }

    public static <K, V extends Keyed<K>> KeyedRegistry<K, V> ordered(Class<? super V> vClass, int initSize) {
        return new KeyedRegistry<>(vClass, new LinkedHashMap<>(initSize));
    }

    public static <K, V extends Keyed<K>> KeyedRegistry<K, V> unordered(Class<? super V> vClass) {
        return new KeyedRegistry<>(vClass, new HashMap<>());
    }

    public static <K, V extends Keyed<K>> KeyedRegistry<K, V> unordered(Class<? super V> vClass, int initSize) {
        return new KeyedRegistry<>(vClass, new HashMap<>(initSize));
    }

    @Override
    public Class<? super V> valueClass() {
        return vClass;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public Optional<V> get(K key) {
        return map.containsKey(key) ? Optional.of(map.get(key)) : Optional.empty();
    }

    @Override
    public K keyOf(V val) {
        return val.getKey();
    }

    @Override
    public void put(K key, V val) {
        map.put(key, val);
    }

    @Override
    public Collection<K> keys() {
        return map.keySet();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    @Override
    public Stream<Entry<K, V>> entries() {
        return map.entrySet().stream()
                .map(e -> new Entry<>(e.getKey(), e.getValue()));
    }
}
