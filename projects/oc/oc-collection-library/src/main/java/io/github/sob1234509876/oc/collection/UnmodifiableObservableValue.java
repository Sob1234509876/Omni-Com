package io.github.sob1234509876.oc.collection;

import java.util.function.BiConsumer;

class UnmodifiableObservableValue<T> implements Observable<T> {

    private final Observable<T> delegate;

    UnmodifiableObservableValue(Observable<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T get() {
        return delegate.get();
    }

    @Override
    public void set(T value) {
        throw new UnsupportedOperationException("unmodifiable observable value");
    }

    @Override
    public Subscription onChange(BiConsumer<? super T, ? super T> listener) {
        return delegate.onChange(listener);
    }

    @Override
    public Observable<T> unmodifiable() {
        return this;
    }

}
