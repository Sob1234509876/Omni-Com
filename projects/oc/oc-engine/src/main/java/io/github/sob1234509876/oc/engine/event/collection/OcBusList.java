package io.github.sob1234509876.oc.engine.event.collection;

import io.github.sob1234509876.oc.engine.api.collection.BusList;
import io.github.sob1234509876.oc.engine.event.OcContentUpdateEventFactory;
import io.github.sob1234509876.oc.engine.event.bus.OcEventBus;
import lombok.*;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcBusList<@NonNull E> extends OcEventBus implements BusList<@NonNull E> {

    @NonNull
    OcContentUpdateEventFactory eventFactory;

    @NonNull
    List<@NonNull E> internal = new LinkedList<>();

    @Override
    public @NonNull List<@NonNull E> asUnmodifiableList() {
        return Collections.unmodifiableList(internal);
    }

    @Override
    public void add(@NonNull E e) {
        internal.add(e);
        post(eventFactory.create(List.of(e)));
    }

    @Override
    public void add(@NonNull E[] e) {
        Collections.addAll(internal, e);
        post(eventFactory.create(List.of(e)));
    }

    @Override
    public void add(@NonNull Collection<? extends E> es) {
        internal.addAll(es);
        post(eventFactory.create(List.copyOf(es)));
    }

    @Override
    public E get(int i) {
        return internal.get(i);
    }

    @Override
    public void postEvent() {
        post(eventFactory.create(Collections.emptyList()));
    }

}
