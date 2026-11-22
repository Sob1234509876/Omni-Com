package io.github.sob1234509876.oc.collection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

class DefaultObservableValue<T> implements Observable<T> {

    private final List<BiConsumer<? super T, ? super T>> listeners = new ArrayList<>();
    private T value;

    DefaultObservableValue(T initialValue) {
        this.value = initialValue;
    }

    @Override
    public T get() {
        return value;
    }

    @Override
    public void set(T value) {
        T old = this.value;
        if (old == value || (old != null && old.equals(value))) {
            return;
        }
        this.value = value;
        for (BiConsumer<? super T, ? super T> listener : new ArrayList<>(listeners)) {
            listener.accept(old, value);
        }
    }

    @Override
    public Subscription onChange(BiConsumer<? super T, ? super T> listener) {
        listeners.add(listener);
        return new DefaultSubscription(() -> listeners.remove(listener));
    }

    @Override
    public Observable<T> unmodifiable() {
        return new UnmodifiableObservableValue<>(this);
    }

}
