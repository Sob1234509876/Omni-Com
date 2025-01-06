package top.sob.platform.api.misc.collections;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public interface ListMap<K, V> extends Map<K, List<? extends V>> {

    List<? extends V> createList(K o);

    default void makeAvailable(K o) {
        if (!containsKey(o))
            put(o, createList(o));
    }

    default int size(K key) {

        makeAvailable(key);

        return get(key).size();
    }

    default boolean isEmpty(K key) {

        makeAvailable(key);

        return get(key).isEmpty();
    }

    default boolean contains(K key, V value) {

        makeAvailable(key);

        return get(key).contains(value);
    }

    @SuppressWarnings("unchecked")
    default Iterator<V> iterator(K key) {

        makeAvailable(key);

        return (Iterator<V>) get(key).iterator();
    }

    default Object[] toArray(K key) {

        makeAvailable(key);

        return get(key).toArray();
    }

    default <T> T[] toArray(K key, T[] a) {

        makeAvailable(key);

        return get(key).toArray(a);
    }

    @SuppressWarnings("unchecked")
    default boolean add(K key, V value) {

        makeAvailable(key);

        return ((List<V>) get(key)).add(value);
    }

    @SuppressWarnings("unchecked")
    default boolean addAll(K key, Collection<? extends V> c) {

        makeAvailable(key);

        return ((List<V>) get(key)).addAll(c);
    }

}
