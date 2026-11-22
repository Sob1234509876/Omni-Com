package io.github.sob1234509876.oc.collection;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

@RequiredArgsConstructor
class DefaultObservableSet<E> implements ObservableSet<E> {

    @NonNull
    private final Set<E> backing;
    private final List<Consumer<? super E>> addListeners = new ArrayList<>();
    private final List<Consumer<? super E>> removeListeners = new ArrayList<>();
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
    public boolean contains(Object o) {
        return backing.contains(o);
    }

    @Override
    public Iterator<E> iterator() {
        return new ObservableSetIterator(backing.iterator());
    }

    @Override
    public Object[] toArray() {
        return backing.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return backing.toArray(a);
    }

    @Override
    public boolean add(E element) {
        boolean added = backing.add(element);
        if (added) {
            fireAdd(element);
        }
        return added;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean remove(Object o) {
        boolean removed = backing.remove(o);
        if (removed) {
            fireRemove((E) o);
        }
        return removed;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return backing.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E element : c) {
            changed |= add(element);
        }
        return changed;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return removeIf(element -> !c.contains(element));
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return removeIf(c::contains);
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        boolean changed = false;
        Iterator<E> iterator = backing.iterator();
        while (iterator.hasNext()) {
            E element = iterator.next();
            if (filter.test(element)) {
                iterator.remove();
                fireRemove(element);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public void clear() {
        if (backing.isEmpty()) {
            return;
        }
        List<E> removed = new ArrayList<>(backing);
        backing.clear();
        for (E element : removed) {
            fireRemove(element);
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

    private void fireAdd(E element) {
        for (Consumer<? super E> listener : new ArrayList<>(addListeners)) {
            listener.accept(element);
        }
    }

    private void fireRemove(E element) {
        for (Consumer<? super E> listener : new ArrayList<>(removeListeners)) {
            listener.accept(element);
        }
    }

    private void fireClear() {
        for (Runnable listener : new ArrayList<>(clearListeners)) {
            listener.run();
        }
    }

    @Override
    public Subscription onAdd(Consumer<? super E> listener) {
        addListeners.add(listener);
        return new DefaultSubscription(() -> addListeners.remove(listener));
    }

    @Override
    public Subscription onRemove(Consumer<? super E> listener) {
        removeListeners.add(listener);
        return new DefaultSubscription(() -> removeListeners.remove(listener));
    }

    @Override
    public Subscription onClear(Runnable listener) {
        clearListeners.add(listener);
        return new DefaultSubscription(() -> clearListeners.remove(listener));
    }

    @Override
    public ObservableSet<E> unmodifiable() {
        return new UnmodifiableObservableSet<>(this);
    }

    private final class ObservableSetIterator implements Iterator<E> {

        private final Iterator<E> backing;
        private E last;

        ObservableSetIterator(Iterator<E> backing) {
            this.backing = backing;
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public E next() {
            last = backing.next();
            return last;
        }

        @Override
        public void remove() {
            E removed = last;
            backing.remove();
            fireRemove(removed);
        }

    }

}
