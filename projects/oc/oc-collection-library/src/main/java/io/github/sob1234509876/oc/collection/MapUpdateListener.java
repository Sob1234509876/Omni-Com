package io.github.sob1234509876.oc.collection;

@FunctionalInterface
public interface MapUpdateListener<K, V> {

    void onUpdate(K key, V oldValue, V newValue);

}
