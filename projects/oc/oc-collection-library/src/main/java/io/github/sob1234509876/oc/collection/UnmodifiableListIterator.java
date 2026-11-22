package io.github.sob1234509876.oc.collection;

import java.util.ListIterator;

class UnmodifiableListIterator<E> implements ListIterator<E> {

    private final ListIterator<E> delegate;

    UnmodifiableListIterator(ListIterator<E> delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public E next() {
        return delegate.next();
    }

    @Override
    public boolean hasPrevious() {
        return delegate.hasPrevious();
    }

    @Override
    public E previous() {
        return delegate.previous();
    }

    @Override
    public int nextIndex() {
        return delegate.nextIndex();
    }

    @Override
    public int previousIndex() {
        return delegate.previousIndex();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("unmodifiable list iterator");
    }

    @Override
    public void set(E e) {
        throw new UnsupportedOperationException("unmodifiable list iterator");
    }

    @Override
    public void add(E e) {
        throw new UnsupportedOperationException("unmodifiable list iterator");
    }

}
