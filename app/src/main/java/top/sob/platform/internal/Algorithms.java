package top.sob.platform.internal;

import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.misc.util.Functions;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Algorithms {

    private Algorithms() {
    }

    @Unmodifiable
    @SuppressWarnings("unchecked")
    public static <T> List<T> bfs(Collection<? extends T> startingNodes,
                                  Function<? extends T, ? extends T[]> nodeSupplier,
                                  Predicate<? extends T> addToResult,
                                  Predicate<? extends T[]> end) {

        var ns = (Function<T, T[]>) nodeSupplier;
        var atr = (Predicate<T>) addToResult;
        var e = (Predicate<T[]>) end;

        var queue = new LinkedList<>(startingNodes);
        var result = new LinkedList<T>();

        while (!queue.isEmpty()) {
            var tt = queue.pollFirst();

            if (atr.test(tt))
                result.add(tt);

            if (e.test((T[]) result.toArray()))
                break;

            Collections.addAll(result, ns.apply(tt));

        }

        return Collections.unmodifiableList(result);

    }

    public static <T> List<T> bfs(Collection<? extends T> startingNodes,
                                  Function<? extends T, ? extends T[]> nodeSupplier,
                                  Predicate<? extends T> addToResult) {
        return bfs(startingNodes, nodeSupplier, addToResult, Functions::alwaysFalse);
    }

    public static <T> List<T> bfs(Collection<? extends T> startingNodes,
                                  Function<? extends T, ? extends T[]> nodeSupplier) {
        return bfs(startingNodes, nodeSupplier, Functions::alwaysTrue);
    }

    public static <T> List<T> bfs(T startingNode,
                                  Function<? extends T, ? extends T[]> nodeSupplier) {
        return bfs(Collections.singleton(startingNode), nodeSupplier);
    }

    public static <T> List<T> bfs(T startingNodes,
                                  Function<? extends T, ? extends T[]> nodeSupplier,
                                  Predicate<? extends T> addToResult,
                                  Predicate<? extends T[]> end) {
        return bfs(Collections.singleton(startingNodes), nodeSupplier, addToResult, end);
    }

}
