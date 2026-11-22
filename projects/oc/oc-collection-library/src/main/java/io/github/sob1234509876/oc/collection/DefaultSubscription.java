package io.github.sob1234509876.oc.collection;

final class DefaultSubscription implements Subscription {

    private final Runnable unsubscriber;
    private boolean closed;

    DefaultSubscription(Runnable unsubscriber) {
        this.unsubscriber = unsubscriber;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        unsubscriber.run();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

}
