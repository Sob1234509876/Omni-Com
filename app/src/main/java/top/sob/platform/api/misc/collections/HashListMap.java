package top.sob.platform.api.misc.collections;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class HashListMap<K, V> extends HashMap<K, List<? extends V>> implements ListMap<K, V> {
    public HashListMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }

    public HashListMap(int initialCapacity) {
        super(initialCapacity);
    }

    public HashListMap() {
    }

    public HashListMap(Map<? extends K, ? extends List<? extends V>> m) {
        super(m);
    }

    @Override
    public List<? extends V> createList(K o) {
        return new LinkedList<>();
    }
}
