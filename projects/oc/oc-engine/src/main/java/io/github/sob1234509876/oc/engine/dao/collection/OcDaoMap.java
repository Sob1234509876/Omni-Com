package io.github.sob1234509876.oc.engine.dao.collection;

import io.github.sob1234509876.oc.engine.AbstractParent;
import io.github.sob1234509876.oc.engine.api.collection.ParentMap;
import io.github.sob1234509876.oc.engine.misc.OcPair;
import lombok.*;
import org.springframework.data.repository.CrudRepository;

import java.util.*;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcDaoMap<@NonNull K, @NonNull V> extends AbstractParent implements ParentMap<@NonNull K, @NonNull V> {

    @NonNull
    CrudRepository<@NonNull OcPair<@NonNull K, @NonNull V>, K> dao;

    @Override
    public int size() {
        return Math.toIntExact(dao.count());
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean containsKey(@NonNull Object key) {
        return dao.existsById((K) key);
    }

    @Override
    public boolean containsValue(@NonNull Object value) {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public V get(@NonNull Object key) {
        var res = dao.findById((K) key);
        return res.orElseThrow(NullPointerException::new)
                .getValue();
    }

    @Override
    @NonNull
    public V put(@NonNull K key, @NonNull V value) {

        var last = Optional.<V>empty();
        if (containsKey(key))
            last = Optional.of(get(key));

        dao.save(new OcPair<>(key, value));

        return last.orElseThrow(NullPointerException::new);
    }

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public V remove(@NonNull Object key) {

        var last = Optional.<V>empty();
        if (containsKey(key))
            last = Optional.of(get(key));

        dao.deleteById((K) key);

        return last.orElseThrow(NullPointerException::new);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void putAll(@NonNull Map<@NonNull ? extends K, @NonNull ? extends V> m) {
        dao.saveAll(m.entrySet()
                .stream()
                .map(OcPair::from)
                .map(p -> (OcPair<@NonNull K, @NonNull V>) p)
                .toList());
    }

    @Override
    public void clear() {
        dao.deleteAll();
    }

    @Override
    public @NonNull Set<@NonNull K> keySet() {
        var tmp = new LinkedList<@NonNull OcPair<@NonNull K, @NonNull V>>();

        dao.findAll()
                .forEach(tmp::add);

        return tmp.stream()
                .map(OcPair::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public @NonNull Collection<@NonNull V> values() {
        var tmp = new LinkedList<@NonNull OcPair<@NonNull K, @NonNull V>>();

        dao.findAll()
                .forEach(tmp::add);

        return tmp.stream()
                .map(OcPair::getValue)
                .toList();
    }

    @Override
    public @NonNull Set<@NonNull Entry<@NonNull K, @NonNull V>> entrySet() {
        var tmp = new HashSet<@NonNull Entry<@NonNull K, @NonNull V>>();

        dao.findAll()
                .forEach(p -> tmp.add(p.toEntry()));

        return tmp;
    }
}
