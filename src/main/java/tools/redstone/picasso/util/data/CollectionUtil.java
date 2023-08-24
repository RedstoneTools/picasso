package tools.redstone.picasso.util.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class CollectionUtil {

    /**
     * Check whether any item in the given array matches the predicate.
     *
     * @param arr The array.
     * @param predicate The predicate.
     * @param <T> The element type.
     * @return Whether any elements match.
     */
    public static <T> boolean anyMatch(T[] arr, Predicate<T> predicate) {
        for (int i = 0, n = arr.length; i < n; i++)
            if (predicate.test(arr[i]))
                return true;
        return false;
    }

    public static <T, T2> List<T2> mapImmediate(List<T> list, Function<T, T2> mapper) {
        final List<T2> res = new ArrayList<>(list.size());
        for (int i = 0, n = list.size(); i < n; i++)
            res.add(mapper.apply(list.get(i)));
        return res;
    }

    @SafeVarargs
    public static <T, T2> void mapImmediate(Collection<T> list, Function<T, T2> mapper, Collection<? super T2>... dest) {
        for (T v1 : list) {
            T2 val = mapper.apply(v1);
            for (var l : dest)
                l.add(val);
        }
    }

    public static <T> void addIfNotNull(Collection<T> collection, T val) {
        if (val != null) collection.add(val);
    }

}
