package io.github.sob1234509876.oc.collection;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@RequiredArgsConstructor
class DefaultObservableList<E> implements ObservableList<E> {

    @NonNull
    private final List<E> backing;
    private final List<Consumer<? super E>> addListeners = new ArrayList<>();
    private final List<Consumer<? super E>> removeListeners = new ArrayList<>();
    private final List<ListReplaceListener<? super E>> replaceListeners = new ArrayList<>();
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
        return listIterator();
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
    public boolean addAll(int index, Collection<? extends E> c) {
        boolean changed = false;
        for (E element : c) {
            add(index, element);
            index++;
            changed = true;
        }
        return changed;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return removeIf(c::contains);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return removeIf(element -> !c.contains(element));
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

    @Override
    public E get(int index) {
        return backing.get(index);
    }

    @Override
    public E set(int index, E element) {
        E old = backing.set(index, element);
        if (!Objects.equals(old, element)) {
            fireReplace(index, old, element);
        }
        return old;
    }

    @Override
    public void add(int index, E element) {
        backing.add(index, element);
        fireAdd(element);
    }

    @Override
    public E remove(int index) {
        E removed = backing.remove(index);
        fireRemove(removed);
        return removed;
    }

    @Override
    public int indexOf(Object o) {
        return backing.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return backing.lastIndexOf(o);
    }

    @Override
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new ObservableListIterator(backing.listIterator(index));
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        Objects.checkFromToIndex(fromIndex, toIndex, size());
        return new ObservableSubList(fromIndex, toIndex);
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        List<E> mapped = new ArrayList<>(backing);
        mapped.replaceAll(operator);
        for (int i = 0; i < mapped.size(); i++) {
            set(i, mapped.get(i));
        }
    }

    @Override
    public void sort(Comparator<? super E> comparator) {
        List<E> sorted = new ArrayList<>(backing);
        sorted.sort(comparator);
        for (int i = 0; i < sorted.size(); i++) {
            set(i, sorted.get(i));
        }
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

    private void fireReplace(int index, E oldElement, E newElement) {
        for (ListReplaceListener<? super E> listener : new ArrayList<>(replaceListeners)) {
            listener.onReplace(index, oldElement, newElement);
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
    public Subscription onReplace(ListReplaceListener<? super E> listener) {
        replaceListeners.add(listener);
        return new DefaultSubscription(() -> replaceListeners.remove(listener));
    }

    @Override
    public Subscription onClear(Runnable listener) {
        clearListeners.add(listener);
        return new DefaultSubscription(() -> clearListeners.remove(listener));
    }

    @Override
    public ObservableList<E> unmodifiable() {
        return new UnmodifiableObservableList<>(this);
    }

    private final class ObservableListIterator implements ListIterator<E> {

        private final ListIterator<E> backing;
        private int index = -1;
        private E last;

        ObservableListIterator(ListIterator<E> backing) {
            this.backing = backing;
        }

        @Override
        public boolean hasNext() {
            return backing.hasNext();
        }

        @Override
        public E next() {
            E next = backing.next();
            index = backing.previousIndex();
            last = next;
            return next;
        }

        @Override
        public boolean hasPrevious() {
            return backing.hasPrevious();
        }

        @Override
        public E previous() {
            E previous = backing.previous();
            index = backing.nextIndex();
            last = previous;
            return previous;
        }

        @Override
        public int nextIndex() {
            return backing.nextIndex();
        }

        @Override
        public int previousIndex() {
            return backing.previousIndex();
        }

        @Override
        public void remove() {
            E removed = last;
            backing.remove();
            fireRemove(removed);
        }

        @Override
        public void set(E element) {
            backing.set(element);
            if (!Objects.equals(last, element)) {
                fireReplace(index, last, element);
            }
        }

        @Override
        public void add(E element) {
            backing.add(element);
            index = -1;
            fireAdd(element);
        }

    }

    private final class ObservableSubList implements List<E> {

        private int offset;
        private int size;

        ObservableSubList(int fromIndex, int toIndex) {
            offset = fromIndex;
            size = toIndex - fromIndex;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public boolean contains(Object o) {
            return indexOf(o) >= 0;
        }

        @Override
        public Iterator<E> iterator() {
            return listIterator();
        }

        @Override
        public Object[] toArray() {
            Object[] array = new Object[size];
            for (int i = 0; i < size; i++) {
                array[i] = get(i);
            }
            return array;
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return snapshot().toArray(a);
        }

        @Override
        public boolean add(E element) {
            add(size, element);
            return true;
        }

        @Override
        public boolean remove(Object o) {
            int index = indexOf(o);
            if (index < 0) {
                return false;
            }
            remove(index);
            return true;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            for (Object o : c) {
                if (!contains(o)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            return addAll(size, c);
        }

        @Override
        public boolean addAll(int index, Collection<? extends E> c) {
            Objects.checkIndex(index, size + 1);
            boolean changed = false;
            for (E element : c) {
                add(index, element);
                index++;
                changed = true;
            }
            return changed;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return removeIf(c::contains);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return removeIf(element -> !c.contains(element));
        }

        @Override
        public boolean removeIf(Predicate<? super E> filter) {
            boolean changed = false;
            ListIterator<E> iterator = listIterator();
            while (iterator.hasNext()) {
                if (filter.test(iterator.next())) {
                    iterator.remove();
                    changed = true;
                }
            }
            return changed;
        }

        @Override
        public void clear() {
            ListIterator<E> iterator = listIterator();
            while (iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }

        @Override
        public boolean equals(Object obj) {
            return snapshot().equals(obj);
        }

        @Override
        public int hashCode() {
            return snapshot().hashCode();
        }

        @Override
        public String toString() {
            return snapshot().toString();
        }

        @Override
        public E get(int index) {
            Objects.checkIndex(index, size);
            return DefaultObservableList.this.get(offset + index);
        }

        @Override
        public E set(int index, E element) {
            Objects.checkIndex(index, size);
            return DefaultObservableList.this.set(offset + index, element);
        }

        @Override
        public void add(int index, E element) {
            Objects.checkIndex(index, size + 1);
            DefaultObservableList.this.add(offset + index, element);
            size++;
        }

        @Override
        public E remove(int index) {
            Objects.checkIndex(index, size);
            E removed = DefaultObservableList.this.remove(offset + index);
            size--;
            return removed;
        }

        @Override
        public int indexOf(Object o) {
            for (int i = 0; i < size; i++) {
                if (Objects.equals(get(i), o)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
            for (int i = size - 1; i >= 0; i--) {
                if (Objects.equals(get(i), o)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public ListIterator<E> listIterator() {
            return listIterator(0);
        }

        @Override
        public ListIterator<E> listIterator(int index) {
            Objects.checkIndex(index, size + 1);
            return new ObservableSubListIterator(index);
        }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            Objects.checkFromToIndex(fromIndex, toIndex, size);
            return new ObservableSubList(offset + fromIndex, offset + toIndex);
        }

        @Override
        public void replaceAll(UnaryOperator<E> operator) {
            List<E> mapped = snapshot();
            mapped.replaceAll(operator);
            for (int i = 0; i < mapped.size(); i++) {
                set(i, mapped.get(i));
            }
        }

        @Override
        public void sort(Comparator<? super E> comparator) {
            List<E> sorted = snapshot();
            sorted.sort(comparator);
            for (int i = 0; i < sorted.size(); i++) {
                set(i, sorted.get(i));
            }
        }

        private List<E> snapshot() {
            List<E> copy = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                copy.add(get(i));
            }
            return copy;
        }

        private final class ObservableSubListIterator implements ListIterator<E> {

            private final ListIterator<E> backing;

            ObservableSubListIterator(int index) {
                backing = DefaultObservableList.this.listIterator(offset + index);
            }

            @Override
            public boolean hasNext() {
                return backing.nextIndex() < offset + size;
            }

            @Override
            public E next() {
                if (backing.nextIndex() >= offset + size) {
                    throw new NoSuchElementException();
                }
                return backing.next();
            }

            @Override
            public boolean hasPrevious() {
                return backing.previousIndex() >= offset;
            }

            @Override
            public E previous() {
                if (backing.previousIndex() < offset) {
                    throw new NoSuchElementException();
                }
                return backing.previous();
            }

            @Override
            public int nextIndex() {
                return backing.nextIndex() - offset;
            }

            @Override
            public int previousIndex() {
                return backing.previousIndex() - offset;
            }

            @Override
            public void remove() {
                backing.remove();
                size--;
            }

            @Override
            public void set(E element) {
                backing.set(element);
            }

            @Override
            public void add(E element) {
                backing.add(element);
                size++;
            }

        }

    }

}
