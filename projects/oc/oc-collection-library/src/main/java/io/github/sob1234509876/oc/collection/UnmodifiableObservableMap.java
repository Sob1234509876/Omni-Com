package io.github.sob1234509876.oc.collection;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

class UnmodifiableObservableMap<K, V> implements ObservableMap<K, V> {

    private final ObservableMap<K, V> delegate;

    UnmodifiableObservableMap(ObservableMap<K, V> delegate) {
        this.delegate = delegate;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return delegate.get(key);
    }

    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public V putIfAbsent(K key, V value) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public V replace(K key, V value) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        throw new UnsupportedOperationException("unmodifiable observable map");
    }

    @Override
    public Set<K> keySet() {
        return new UnmodifiableKeySet();
    }

    @Override
    public Collection<V> values() {
        return new UnmodifiableValues();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new UnmodifiableEntrySet();
    }

    @Override
    public Subscription onAdd(BiConsumer<? super K, ? super V> listener) {
        return delegate.onAdd(listener);
    }

    @Override
    public Subscription onUpdate(MapUpdateListener<? super K, ? super V> listener) {
        return delegate.onUpdate(listener);
    }

    @Override
    public Subscription onRemove(BiConsumer<? super K, ? super V> listener) {
        return delegate.onRemove(listener);
    }

    @Override
    public Subscription onClear(Runnable listener) {
        return delegate.onClear(listener);
    }

    @Override
    public ObservableMap<K, V> unmodifiable() {
        return this;
    }

    private final class UnmodifiableEntry implements Entry<K, V> {

        private final Entry<K, V> delegate;

        UnmodifiableEntry(Entry<K, V> delegate) {
            this.delegate = delegate;
        }

        @Override
        public K getKey() {
            return delegate.getKey();
        }

        @Override
        public V getValue() {
            return delegate.getValue();
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException("unmodifiable entry");
        }

        @Override
        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

    }

    private final class UnmodifiableEntrySetIterator implements Iterator<Entry<K, V>> {

        private final Iterator<Entry<K, V>> delegate;

        UnmodifiableEntrySetIterator(Iterator<Entry<K, V>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Entry<K, V> next() {
            return new UnmodifiableEntry(delegate.next());
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("unmodifiable entry set");
        }

    }

    private final class UnmodifiableKeySet implements Set<K> {

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.containsKey(o);
        }

        @Override
        public Iterator<K> iterator() {
            return new UnmodifiableIterator<>(delegate.keySet().iterator());
        }

        @Override
        public Object[] toArray() {
            return delegate.keySet().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.keySet().toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.keySet().containsAll(c);
        }

        @Override
        public boolean add(K e) {
            throw new UnsupportedOperationException("unmodifiable key set");
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException("unmodifiable key set");
        }

        @Override
        public boolean addAll(Collection<? extends K> c) {
            throw new UnsupportedOperationException("unmodifiable key set");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException("unmodifiable key set");
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException("unmodifiable key set");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("unmodifiable key set");
        }

    }

    private final class UnmodifiableValues implements Collection<V> {

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.containsValue(o);
        }

        @Override
        public Iterator<V> iterator() {
            return new UnmodifiableIterator<>(delegate.values().iterator());
        }

        @Override
        public Object[] toArray() {
            return delegate.values().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.values().toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.values().containsAll(c);
        }

        @Override
        public boolean add(V v) {
            throw new UnsupportedOperationException("unmodifiable values");
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException("unmodifiable values");
        }

        @Override
        public boolean addAll(Collection<? extends V> c) {
            throw new UnsupportedOperationException("unmodifiable values");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException("unmodifiable values");
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException("unmodifiable values");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("unmodifiable values");
        }

    }

    private final class UnmodifiableEntrySet implements Set<Entry<K, V>> {

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.entrySet().contains(o);
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new UnmodifiableEntrySetIterator(delegate.entrySet().iterator());
        }

        @Override
        public Object[] toArray() {
            return delegate.entrySet().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.entrySet().toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.entrySet().containsAll(c);
        }

        @Override
        public boolean add(Entry<K, V> e) {
            throw new UnsupportedOperationException("unmodifiable entry set");
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException("unmodifiable entry set");
        }

        @Override
        public boolean addAll(Collection<? extends Entry<K, V>> c) {
            throw new UnsupportedOperationException("unmodifiable entry set");
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException("unmodifiable entry set");
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException("unmodifiable entry set");
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("unmodifiable entry set");
        }

    }

}
