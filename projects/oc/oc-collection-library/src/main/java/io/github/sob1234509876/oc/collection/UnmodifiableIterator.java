package io.github.sob1234509876.oc.collection;

import java.util.Iterator;

class UnmodifiableIterator<E> implements Iterator<E> {

    private final Iterator<E> delegate;

    UnmodifiableIterator(Iterator<E> delegate) {
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
    public void remove() {
        throw new UnsupportedOperationException("unmodifiable iterator");
    }

}
