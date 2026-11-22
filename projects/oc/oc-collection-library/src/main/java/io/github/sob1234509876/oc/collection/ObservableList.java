package io.github.sob1234509876.oc.collection;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

public interface ObservableList<E> extends ObservableCollection<E>, List<E> {

    static <E> ObservableList<E> create() {
        return new DefaultObservableList<>(new ArrayList<>());
    }

    static <E> ObservableList<E> over(List<E> backing) {
        return new DefaultObservableList<>(backing);
    }

    Subscription onReplace(@NonNull ListReplaceListener<? super E> listener);

    @Override
    ObservableList<E> unmodifiable();

}
