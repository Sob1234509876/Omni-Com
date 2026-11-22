package io.github.sob1234509876.oc.collection;

@FunctionalInterface
public interface ListReplaceListener<E> {

    void onReplace(int index, E oldElement, E newElement);

}
