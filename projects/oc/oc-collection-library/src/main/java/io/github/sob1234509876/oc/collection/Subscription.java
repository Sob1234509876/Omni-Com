package io.github.sob1234509876.oc.collection;

public interface Subscription extends AutoCloseable {

    @Override
    void close();

    boolean isClosed();

}
