[English](README.md) | [简体中文](README_zh_CN.md)

# Omni Com Collection Library

Observable extensions of the Java collections framework and the foundation the Spring-Boot-based
Omni Com engine rewrite is built on: `Observable<T>` is a scalar value holder, `ObservableCollection<E>`,
`ObservableSet<E>`, `ObservableList<E>` and `ObservableMap<K,V>` extend their JDK counterparts and fire
events for every real change. The library is a thin decorator layer over any backing `java.util`
collection; the self check pins every documented behavior.

## Coordinates

| | |
| --- | --- |
| Gradle path | `:projects:oc:oc-collection-library` |
| Java module | `oc.projects.oc.collection.library` |
| Base package | `io.github.sob1234509876.oc.collection` |
| Group / version | `io.github.sob1234509876.oc` / `1.0a` |
| Build | `./gradlew :projects:oc:oc-collection-library:build` (works offline once the compile-time-only dependencies are cached) |
| Self check | `./gradlew :projects:oc:oc-collection-library:selfcheck` |

## What it gives you

Zero runtime dependencies. JetBrains annotations and Lombok are compile-time only.

- `Observable<T>` — `create(T)` / `get()` / `set(T)` / `onChange(oldValue, newValue)`;
- `ObservableCollection<E>` — `onAdd(Consumer)` / `onRemove(Consumer)` / `onClear(Runnable)`;
- `ObservableList<E>` adds `onReplace(index, oldElement, newElement)`;
- `ObservableMap<K,V>` adds `onAdd(key, value)`, `onUpdate(key, oldValue, newValue)`,
  `onRemove(key, oldValue)` and `onClear(Runnable)`;
- every hook returns a `Subscription` (`AutoCloseable`, `close()` idempotent, `isClosed()`);
- static factories `create()` (backs with `ArrayList` / `LinkedHashSet` / `LinkedHashMap`, so iteration
  order is deterministic) and `over(...)` (wraps an existing collection — keep the backing collection
  private, mutations that bypass the wrapper fire no events);
- each type also has `unmodifiable()` returning a read-only view.

## Events

1. Events are synchronous: they fire on the mutating thread, after the change has been applied
   (subscribers see the final state).
2. Listeners iterate over a snapshot of the registry: mutating the same collection from inside a
   listener is safe and deterministic (nested events fire immediately, depth first); closing a
   subscription from inside its own callback is safe.
3. Listeners on the same hook fire in registration order.
4. Bulk operations fire one event per element in encounter order; there are no separate bulk events.
   `clear()` fires `onRemove` for every removed element in encounter order and then one `onClear`;
   clearing an empty collection fires nothing.
5. Only real changes fire: adding an element a set already has, `put` with an equal value or `set` with
   an equal element stay silent.
6. `equals`, `hashCode` and `toString` delegate to the backing collection, so the `List`/`Set`/`Map`
   contracts hold unchanged.
7. Mutations through `subList()` are routed through the parent list, so subscribers receive the events
   with absolute parent indices; nested sub-lists behave the same. Structural mutation from inside a
   listener during `sort`/`replaceAll` may shorten the precomputed target positions and throw — the
   outcome is still deterministic.
8. The implementations are not thread safe: the engine uses a single world thread, and callers must
   mutate on that thread.
9. `unmodifiable()` views throw `UnsupportedOperationException` for every mutation, but changes made
   through the original observable still fire on the view's subscriptions.

The decorators intercept every mutation path, including the ones plain wrappers usually miss:
`Iterator.remove`, `ListIterator.set/add`, `subList`, map `keySet()`/`values()`/`entrySet()` views
(iterator removal, `Entry.setValue`), and the whole
`putIfAbsent`/`compute`/`computeIfAbsent`/`computeIfPresent`/`merge`/`replace`/`remove(key,value)`/
`putAll`/`replaceAll` family all fire the same events as their direct counterparts.

## Self check

The self check is a runnable program (no test framework) living in the `selfCheck` source set. Run with
`./gradlew :projects:oc:oc-collection-library:selfcheck`. It covers the event contract above, every
leak path, subscription semantics (idempotent close, ordering), re-entrant mutation, view contracts
(`equals`/`hashCode`/`toString` delegation, read-only rejection with event pass-through) and
deterministic iteration order.
