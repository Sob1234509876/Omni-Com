package io.github.sob1234509876.oc.googology.collection;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.*;

@Data
@NoArgsConstructor
public class ListSortedSequence<@NonNull E extends Comparable<@NonNull E>> implements SortedSequence<@NonNull E> {

    @NonNull
    List<@NonNull E> list = new LinkedList<>();

    @Override
    public @NonNull List<@NonNull E> toList() {
        return Collections.unmodifiableList(list);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public @NonNull Optional<@NonNull E> get(int index) {
        if (list.size() <= index)
            return Optional.empty();

        return Optional.of(list.get(index));
    }

    @Override
    public void set(int index, @NonNull E e) {
        remove(index);
        add(e);
    }

    @Override
    public void add(@NonNull E e) {
        var i = Collections.binarySearch(list, e, Comparator.reverseOrder());

        if (i < 0)
            i = -i - 1;

        list.add(i, e);
    }

    @Override
    public void add(@NonNull Collection<? extends @NonNull E> collection) {
        collection.forEach(this::add);
    }

    @Override
    public void remove(int index) {
        list.remove(index);
    }

    @Override
    public int compareTo(@NonNull SortedSequence<@NonNull E> o) {
        var lenCmpRes = Integer.compare(size(), o.size());
        var minLen = Math.min(size(), o.size());

        for (var i = 0; i < minLen; i++) {
            var to = get(i);
            var oo = o.get(i);

            if (to.isEmpty() || oo.isEmpty())
                throw new NullPointerException("Variable to or oo is null (this exception shouldn't be reached)");

            var tOrd = to.get();
            var oOrd = oo.get();
            var cr = tOrd.compareTo(oOrd);

            if (cr != 0)
                return cr;
        }

        return lenCmpRes;
    }
}
