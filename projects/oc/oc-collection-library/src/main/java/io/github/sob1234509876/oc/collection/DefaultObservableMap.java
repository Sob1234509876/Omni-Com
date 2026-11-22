package io.github.sob1234509876.oc.collection;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

@RequiredArgsConstructor
class DefaultObservableMap<K, V> implements ObservableMap<K, V> {

    @NonNull
    private final Map<K, V> backing;
    private final List<BiConsumer<? super K, ? super V>> addListeners = new ArrayList<>();
    private final List<MapUpdateListener<? super K, ? super V>> updateListeners = new ArrayList<>();
    private final List<BiConsumer<? super K, ? super V>> removeListeners = new ArrayList<>();
    private final List<Runnable> clearListeners = new ArrayList<>();

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return backing.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return backing.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return backing.get(key);
    }

    @Override
    public V put(K key, V value) {
        boolean existed = backing.containsKey(key);
        V old = backing.put(key, value);
        if (existed) {
            if (!Objects.equals(old, value)) {
                fireUpdate(key, old, value);
            }
        } else {
            fireAdd(key, value);
        }
        return old;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        boolean existed = backing.containsKey(key);
        V old = backing.remove(key);
        if (existed) {
            fireRemove((K) key, old);
        }
        return old;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        if (backing.isEmpty()) {
            return;
        }
        List<K> removedKeys = new ArrayList<>();
        List<V> removedValues = new ArrayList<>();
        for (Entry<K, V> entry : backing.entrySet()) {
            removedKeys.add(entry.getKey());
            removedValues.add(entry.getValue());
        }
        backing.clear();
        for (int i = 0; i < removedKeys.size(); i++) {
            fireRemove(removedKeys.get(i), removedValues.get(i));
        }
        fireClear();
    }

    @Override
    public boolean equals(Object obj) {
        return backing.equals(obj);
    }

    @Override
    public int hashCode() {
        return backing.hashCode();
    }

    @Override
    public String toString() {
        return backing.toString();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V old = backing.get(key);
        if (old != null) {
            return old;
        }
        boolean existed = backing.containsKey(key);
        if (existed && value == null) {
            return null;
        }
        backing.put(key, value);
        if (existed) {
            fireUpdate(key, null, value);
        } else {
            fireAdd(key, value);
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean remove(Object key, Object value) {
        boolean removed = backing.remove(key, value);
        if (removed) {
            fireRemove((K) key, (V) value);
        }
        return removed;
    }

    @Override
    public V replace(K key, V value) {
        if (!backing.containsKey(key)) {
            return null;
        }
        V old = backing.put(key, value);
        if (!Objects.equals(old, value)) {
            fireUpdate(key, old, value);
        }
        return old;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        boolean replaced = backing.replace(key, oldValue, newValue);
        if (replaced) {
            fireUpdate(key, oldValue, newValue);
        }
        return replaced;
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        List<K> keys = new ArrayList<>();
        List<V> olds = new ArrayList<>();
        for (Entry<K, V> entry : backing.entrySet()) {
            keys.add(entry.getKey());
            olds.add(entry.getValue());
        }
        backing.replaceAll(function);
        for (int i = 0; i < keys.size(); i++) {
            V now = backing.get(keys.get(i));
            if (!Objects.equals(olds.get(i), now)) {
                fireUpdate(keys.get(i), olds.get(i), now);
            }
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        V old = backing.get(key);
        if (old != null) {
            return old;
        }
        V newValue = mappingFunction.apply(key);
        if (newValue == null) {
            return null;
        }
        boolean existed = backing.containsKey(key);
        backing.put(key, newValue);
        if (existed) {
            fireUpdate(key, old, newValue);
        } else {
            fireAdd(key, newValue);
        }
        return newValue;
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        V old = backing.get(key);
        if (old == null) {
            return null;
        }
        V newValue = remappingFunction.apply(key, old);
        if (newValue == null) {
            backing.remove(key);
            fireRemove(key, old);
            return null;
        }
        backing.put(key, newValue);
        if (!Objects.equals(old, newValue)) {
            fireUpdate(key, old, newValue);
        }
        return newValue;
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        boolean existed = backing.containsKey(key);
        V old = backing.get(key);
        V newValue = remappingFunction.apply(key, old);
        if (newValue == null) {
            if (existed) {
                backing.remove(key);
                fireRemove(key, old);
            }
            return null;
        }
        backing.put(key, newValue);
        if (existed) {
            if (!Objects.equals(old, newValue)) {
                fireUpdate(key, old, newValue);
            }
        } else {
            fireAdd(key, newValue);
        }
        return newValue;
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        boolean existed = backing.containsKey(key);
        V old = backing.get(key);
        V newValue = old == null ? value : remappingFunction.apply(old, value);
        if (newValue == null) {
            if (existed) {
                backing.remove(key);
                fireRemove(key, old);
            }
            return null;
        }
        backing.put(key, newValue);
        if (existed) {
            if (!Objects.equals(old, newValue)) {
                fireUpdate(key, old, newValue);
            }
        } else {
            fireAdd(key, newValue);
        }
        return newValue;
    }

    @Override
    public Set<K> keySet() {
        return new ObservableKeySet();
    }

    @Override
    public Collection<V> values() {
        return new ObservableValues();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new ObservableEntrySet();
    }

    private void fireAdd(K key, V value) {
        for (BiConsumer<? super K, ? super V> listener : new ArrayList<>(addListeners)) {
            listener.accept(key, value);
        }
    }

    private void fireUpdate(K key, V oldValue, V newValue) {
        for (MapUpdateListener<? super K, ? super V> listener : new ArrayList<>(updateListeners)) {
            listener.onUpdate(key, oldValue, newValue);
        }
    }

    private void fireRemove(K key, V oldValue) {
        for (BiConsumer<? super K, ? super V> listener : new ArrayList<>(removeListeners)) {
            listener.accept(key, oldValue);
        }
    }

    private void fireClear() {
        for (Runnable listener : new ArrayList<>(clearListeners)) {
            listener.run();
        }
    }

    @Override
    public Subscription onAdd(BiConsumer<? super K, ? super V> listener) {
        addListeners.add(listener);
        return new DefaultSubscription(() -> addListeners.remove(listener));
    }

    @Override
    public Subscription onUpdate(MapUpdateListener<? super K, ? super V> listener) {
        updateListeners.add(listener);
        return new DefaultSubscription(() -> updateListeners.remove(listener));
    }

    @Override
    public Subscription onRemove(BiConsumer<? super K, ? super V> listener) {
        removeListeners.add(listener);
        return new DefaultSubscription(() -> removeListeners.remove(listener));
    }

    @Override
    public Subscription onClear(Runnable listener) {
        clearListeners.add(listener);
        return new DefaultSubscription(() -> clearListeners.remove(listener));
    }

    @Override
    public ObservableMap<K, V> unmodifiable() {
        return new UnmodifiableObservableMap<>(this);
    }

    private final class ObservableEntry implements Entry<K, V> {

        private final Entry<K, V> backing;

        ObservableEntry(Entry<K, V> backing) {
            this.backing = backing;
        }

        @Override
        public K getKey() {
            return backing.getKey();
        }

        @Override
        public V getValue() {
            return backing.getValue();
        }

        @Override
        public V setValue(V value) {
            return DefaultObservableMap.this.put(getKey(), value);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Entry<?, ?> entry)) {
                return false;
            }
            return Objects.equals(backing.getKey(), entry.getKey()) && Objects.equals(backing.getValue(), entry.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(backing.getKey()) ^ Objects.hashCode(backing.getValue());
        }

        @Override
        public String toString() {
            return backing.getKey() + "=" + backing.getValue();
        }

    }

    private final class ObservableEntrySetIterator implements Iterator<Entry<K, V>> {

        private final Iterator<Entry<K, V>> backing;
        private Entry<K, V> last;

        ObservableEntrySetIterator(Iterator<Entry<K, V>> backing) {
            this.backing = backing;
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public Entry<K, V> next() {
            last = backing.next();
            return new ObservableEntry(last);
        }

        @Override
        public void remove() {
            Entry<K, V> removed = last;
            backing.remove();
            fireRemove(removed.getKey(), removed.getValue());
        }

    }

    private final class ObservableKeySetIterator implements Iterator<K> {

        private final Iterator<Entry<K, V>> backing;
        private Entry<K, V> last;

        ObservableKeySetIterator(Iterator<Entry<K, V>> backing) {
            this.backing = backing;
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public K next() {
            last = backing.next();
            return last.getKey();
        }

        @Override
        public void remove() {
            Entry<K, V> removed = last;
            backing.remove();
            fireRemove(removed.getKey(), removed.getValue());
        }

    }

    private final class ObservableValuesIterator implements Iterator<V> {

        private final Iterator<Entry<K, V>> backing;
        private Entry<K, V> last;

        ObservableValuesIterator(Iterator<Entry<K, V>> backing) {
            this.backing = backing;
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public V next() {
            last = backing.next();
            return last.getValue();
        }

        @Override
        public void remove() {
            Entry<K, V> removed = last;
            backing.remove();
            fireRemove(removed.getKey(), removed.getValue());
        }

    }

    private final class ObservableKeySet implements Set<K> {

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return backing.containsKey(o);
        }

        @Override
        public Iterator<K> iterator() {
            return new ObservableKeySetIterator(backing.entrySet().iterator());
        }

        @Override
        public Object[] toArray() {
            return backing.keySet().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return backing.keySet().toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return backing.keySet().containsAll(c);
        }

        @Override
        public boolean add(K e) {
            throw new UnsupportedOperationException("keySet is read only");
        }

        @Override
        public boolean remove(Object o) {
            boolean existed = backing.containsKey(o);
            if (existed) {
                DefaultObservableMap.this.remove(o);
            }
            return existed;
        }

        @Override
        public boolean addAll(Collection<? extends K> c) {
            throw new UnsupportedOperationException("keySet is read only");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return removeIf(key -> !c.contains(key));
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return removeIf(c::contains);
        }

        @Override
        public boolean removeIf(Predicate<? super K> filter) {
            boolean changed = false;
            Iterator<Entry<K, V>> iterator = backing.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<K, V> entry = iterator.next();
                if (filter.test(entry.getKey())) {
                    V old = entry.getValue();
                    iterator.remove();
                    fireRemove(entry.getKey(), old);
                    changed = true;
                }
            }
            return changed;
        }

        @Override
        public void clear() {
            DefaultObservableMap.this.clear();
        }

        @Override
        public boolean equals(Object obj) {
            return new LinkedHashSet<>(this).equals(obj);
        }

        @Override
        public int hashCode() {
            return new LinkedHashSet<>(this).hashCode();
        }

        @Override
        public String toString() {
            return new LinkedHashSet<>(this).toString();
        }

    }

    private final class ObservableValues implements Collection<V> {

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return backing.containsValue(o);
        }

        @Override
        public Iterator<V> iterator() {
            return new ObservableValuesIterator(backing.entrySet().iterator());
        }

        @Override
        public Object[] toArray() {
            return backing.values().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return backing.values().toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return backing.values().containsAll(c);
        }

        @Override
        public boolean add(V v) {
            throw new UnsupportedOperationException("values is read only");
        }

        @Override
        public boolean remove(Object o) {
            for (Entry<K, V> entry : backing.entrySet()) {
                if (Objects.equals(entry.getValue(), o)) {
                    DefaultObservableMap.this.remove(entry.getKey());
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean addAll(Collection<? extends V> c) {
            throw new UnsupportedOperationException("values is read only");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return removeIf(value -> !c.contains(value));
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return removeIf(c::contains);
        }

        @Override
        public boolean removeIf(Predicate<? super V> filter) {
            boolean changed = false;
            Iterator<Entry<K, V>> iterator = backing.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<K, V> entry = iterator.next();
                if (filter.test(entry.getValue())) {
                    K key = entry.getKey();
                    V old = entry.getValue();
                    iterator.remove();
                    fireRemove(key, old);
                    changed = true;
                }
            }
            return changed;
        }

        @Override
        public void clear() {
            DefaultObservableMap.this.clear();
        }

    }

    private final class ObservableEntrySet implements Set<Entry<K, V>> {

        @Override
        public int size() {
            return backing.size();
        }

        @Override
        public boolean isEmpty() {
            return backing.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return backing.entrySet().contains(o);
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new ObservableEntrySetIterator(backing.entrySet().iterator());
        }

        @Override
        public Object[] toArray() {
            return backing.entrySet().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return backing.entrySet().toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return backing.entrySet().containsAll(c);
        }

        @Override
        public boolean add(Entry<K, V> e) {
            throw new UnsupportedOperationException("entrySet is read only");
        }

        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Entry<?, ?> entry)) {
                return false;
            }
            boolean existed = backing.entrySet().contains(o);
            if (existed) {
                DefaultObservableMap.this.remove(entry.getKey());
            }
            return existed;
        }

        @Override
        public boolean addAll(Collection<? extends Entry<K, V>> c) {
            throw new UnsupportedOperationException("entrySet is read only");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return removeIf(entry -> !c.contains(entry));
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return removeIf(c::contains);
        }

        @Override
        public boolean removeIf(Predicate<? super Entry<K, V>> filter) {
            boolean changed = false;
            Iterator<Entry<K, V>> iterator = backing.entrySet().iterator();
            while (iterator.hasNext()) {
                Entry<K, V> entry = iterator.next();
                if (filter.test(new ObservableEntry(entry))) {
                    K key = entry.getKey();
                    V old = entry.getValue();
                    iterator.remove();
                    fireRemove(key, old);
                    changed = true;
                }
            }
            return changed;
        }

        @Override
        public void clear() {
            DefaultObservableMap.this.clear();
        }

        @Override
        public boolean equals(Object obj) {
            return new LinkedHashSet<>(this).equals(obj);
        }

        @Override
        public int hashCode() {
            return new LinkedHashSet<>(this).hashCode();
        }

        @Override
        public String toString() {
            return new LinkedHashSet<>(this).toString();
        }

    }

}
