package io.github.sob1234509876.oc.collection;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.UnaryOperator;

class UnmodifiableList<E> implements List<E> {

    private final List<E> delegate;

    UnmodifiableList(List<E> delegate) {
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
        return new UnmodifiableListIterator<>(delegate.listIterator());
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
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("unmodifiable list");
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
    public E get(int index) {
        return delegate.get(index);
    }

    @Override
    public E set(int index, E element) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public void add(int index, E element) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public E remove(int index) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public int indexOf(Object o) {
        return delegate.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return delegate.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return new UnmodifiableListIterator<>(delegate.listIterator());
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new UnmodifiableListIterator<>(delegate.listIterator(index));
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        return new UnmodifiableList<>(delegate.subList(fromIndex, toIndex));
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

    @Override
    public void sort(Comparator<? super E> c) {
        throw new UnsupportedOperationException("unmodifiable list");
    }

}
