package io.github.sob1234509876.oc.collection;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

class UnmodifiableObservableSet<E> implements ObservableSet<E> {

    private final ObservableSet<E> delegate;

    UnmodifiableObservableSet(ObservableSet<E> delegate) {
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
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return new UnmodifiableIterator<>(delegate.iterator());
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException("unmodifiable observable set");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("unmodifiable observable set");
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException("unmodifiable observable set");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("unmodifiable observable set");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("unmodifiable observable set");
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        throw new UnsupportedOperationException("unmodifiable observable set");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("unmodifiable observable set");
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
    public Subscription onAdd(Consumer<? super E> listener) {
        return delegate.onAdd(listener);
    }

    @Override
    public Subscription onRemove(Consumer<? super E> listener) {
        return delegate.onRemove(listener);
    }

    @Override
    public Subscription onClear(Runnable listener) {
        return delegate.onClear(listener);
    }

    @Override
    public ObservableSet<E> unmodifiable() {
        return this;
    }

}
