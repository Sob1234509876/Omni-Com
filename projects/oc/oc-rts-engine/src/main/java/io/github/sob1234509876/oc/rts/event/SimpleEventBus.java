package io.github.sob1234509876.oc.rts.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Default {@link EventBus}: a small ordered listener table, without reflection or threading.
 */
public final class SimpleEventBus implements EventBus {

    private final Map<Class<?>, List<Listener<?>>> listeners = new LinkedHashMap<>();

    /** Creates a bus without listeners; systems subscribe as they are constructed. */
    public SimpleEventBus() {
    }

    @Override
    public <E> Subscription subscribe(Class<E> type, Consumer<E> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        Listener<E> entry = new Listener<>(type, listener);
        listeners.computeIfAbsent(type, key -> new ArrayList<>()).add(entry);
        return entry;
    }

    @Override
    public void publish(Object event) {
        Objects.requireNonNull(event, "event");
        List<Listener<?>> snapshot = new ArrayList<>();
        for (Map.Entry<Class<?>, List<Listener<?>>> group : listeners.entrySet()) {
            if (group.getKey().isInstance(event)) {
                snapshot.addAll(group.getValue());
            }
        }
        for (Listener<?> listener : snapshot) {
            listener.deliver(event);
        }
    }

    /** Number of registered listeners, for debugging. */
    public int listenerCount() {
        return listeners.values().stream().mapToInt(List::size).sum();
    }

    private final class Listener<E> implements Subscription {

        private final Class<E> type;
        private final Consumer<E> consumer;
        private boolean active = true;

        private Listener(Class<E> type, Consumer<E> consumer) {
            this.type = type;
            this.consumer = consumer;
        }

        @SuppressWarnings("unchecked")
        private void deliver(Object event) {
            if (active) {
                consumer.accept((E) event);
            }
        }

        @Override
        public void cancel() {
            if (active) {
                active = false;
                List<Listener<?>> group = listeners.get(type);
                if (group != null) {
                    group.remove(this);
                    if (group.isEmpty()) {
                        listeners.remove(type);
                    }
                }
            }
        }

        @Override
        public boolean active() {
            return active;
        }
    }
}
