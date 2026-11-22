package io.github.sob1234509876.oc.collection.check;

import io.github.sob1234509876.oc.collection.Observable;
import io.github.sob1234509876.oc.collection.ObservableList;
import io.github.sob1234509876.oc.collection.ObservableMap;
import io.github.sob1234509876.oc.collection.ObservableSet;
import io.github.sob1234509876.oc.collection.Subscription;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Self check of the collection library: the event contract, every intercepted leak path, subscription
 * semantics, re-entrant mutation, view contracts and deterministic iteration order.
 * Run with {@code ./gradlew :projects:oc:oc-collection-library:selfcheck}.
 */
public final class CollectionCheck {

    private static int passed;
    private static final List<String> FAILURES = new ArrayList<>();

    private CollectionCheck() {}

    public static void main(String[] args) {
        section("1) Observable 值容器");
        observableValue();
        section("2) List 增删改事件");
        listEvents();
        section("3) List 泄漏路径");
        listLeakPaths();
        section("4) Set 事件");
        setEvents();
        section("5) Map 事件与视图");
        mapEvents();
        section("6) 订阅句柄");
        subscriptions();
        section("7) 重入");
        reentrancy();
        section("8) 契约与不可变视图");
        contracts();
        section("9) 确定性");
        determinism();
        summary();
    }

    private static void observableValue() {
        Observable<String> value = Observable.create("a");
        equal("初值可读", "a", value.get());

        List<String> events = new ArrayList<>();
        Subscription subscription = value.onChange((oldValue, newValue) -> events.add(oldValue + "->" + newValue));
        value.set("a");
        equal("相同值不触发", "[]", events.toString());
        value.set("b");
        equal("变化触发旧新值", "[a->b]", events.toString());
        value.set(null);
        equal("置 null 也触发", "[a->b, b->null]", events.toString());
        value.set(null);
        equal("null 相同不触发", "[a->b, b->null]", events.toString());

        subscription.close();
        value.set("c");
        equal("关闭后不再触发", "[a->b, b->null]", events.toString());
        check("isClosed 为真", subscription.isClosed());

        Subscription counted = value.onChange((oldValue, newValue) -> {
        });
        counted.close();
        counted.close();
        check("close 幂等（重复 close 安全）", counted.isClosed());

        Observable<String> readOnly = value.unmodifiable();
        throwsExactly("不可变视图 set 被拒", UnsupportedOperationException.class, () -> readOnly.set("d"));
        equal("被拒后值不变", "c", readOnly.get());

        List<String> viewEvents = new ArrayList<>();
        Subscription viewSubscription = readOnly.onChange((oldValue, newValue) -> viewEvents.add(oldValue + "->" + newValue));
        value.set("d");
        equal("视图订阅仍收到源头事件", "[c->d]", viewEvents.toString());
        viewSubscription.close();
    }

    private static void listEvents() {
        ObservableList<String> list = ObservableList.create();
        List<String> events = new ArrayList<>();
        list.onAdd(element -> events.add("+" + element));
        list.onRemove(element -> events.add("-" + element));
        list.onReplace((index, oldElement, newElement) -> events.add("~" + index + ":" + oldElement + ">" + newElement));
        list.onClear(() -> events.add("clear"));

        list.add("a");
        equal("add 触发 onAdd", "[+a]", events.toString());
        list.add("b");
        list.addAll(List.of("c", "d"));
        equal("addAll 逐元素按序", "[+a, +b, +c, +d]", events.toString());
        events.clear();

        list.set(1, "x");
        equal("set 变更触发 onReplace", "[~1:b>x]", events.toString());
        list.set(1, "x");
        equal("set 相同元素不触发", "[~1:b>x]", events.toString());
        events.clear();

        list.remove("x");
        equal("remove(Object) 触发 onRemove", "[-x]", events.toString());
        list.remove(0);
        equal("remove(index) 触发 onRemove", "[-x, -a]", events.toString());
        events.clear();

        list.add("q");
        list.removeIf(element -> element.equals("q"));
        equal("removeIf 触发 onRemove", "[+q, -q]", events.toString());
        events.clear();

        list.addAll(List.of("m", "n", "o"));
        events.clear();
        list.removeAll(List.of("m", "o"));
        equal("removeAll 逐元素", "[-m, -o]", events.toString());
        events.clear();
        list.retainAll(List.of("n"));
        equal("retainAll 移除不匹配", "[-c, -d]", events.toString());
        events.clear();
        list.retainAll(List.of("n", "k"));
        equal("retainAll 全保留不触发", "[]", events.toString());
        events.clear();
        list.retainAll(List.of("k"));
        equal("retainAll 移除其余", "[-n]", events.toString());
        events.clear();

        list.add(0, "z");
        equal("指定位置 add 触发 onAdd", "[+z]", events.toString());
        events.clear();

        list.clear();
        equal("clear 逐元素后一次 onClear", "[-z, clear]", events.toString());
        events.clear();
        list.clear();
        equal("空列表 clear 无事件", "[]", events.toString());

        ObservableList<Integer> numbers = ObservableList.over(new ArrayList<>(List.of(3, 1, 2)));
        List<String> replaces = new ArrayList<>();
        numbers.onReplace((index, oldElement, newElement) -> replaces.add(index + ":" + oldElement + ">" + newElement));
        numbers.sort(Comparator.naturalOrder());
        equal("sort 只对实际改变的位置触发", "[0:3>1, 1:1>2, 2:2>3]", replaces.toString());
        replaces.clear();
        numbers.replaceAll(number -> number * 10);
        equal("replaceAll 逐位置触发", "[0:1>10, 1:2>20, 2:3>30]", replaces.toString());
        equal("replaceAll 落地", "[10, 20, 30]", numbers.toString());
    }

    private static void listLeakPaths() {
        ObservableList<String> list = ObservableList.create();
        List<String> events = new ArrayList<>();
        list.onAdd(element -> events.add("+" + element));
        list.onRemove(element -> events.add("-" + element));
        list.onReplace((index, oldElement, newElement) -> events.add("~" + index + ":" + oldElement + ">" + newElement));
        list.addAll(List.of("a", "b", "c", "d"));
        events.clear();

        Iterator<String> iterator = list.iterator();
        iterator.next();
        iterator.remove();
        equal("iterator().remove() 触发 onRemove", "[-a]", events.toString());
        events.clear();

        ListIterator<String> forward = list.listIterator();
        forward.next();
        forward.set("x");
        equal("listIterator().set() 触发 onReplace", "[~0:b>x]", events.toString());
        forward.add("y");
        equal("listIterator().add() 触发 onAdd", "[~0:b>x, +y]", events.toString());
        events.clear();

        ListIterator<String> backwards = list.listIterator(4);
        backwards.previous();
        backwards.remove();
        equal("从尾部 listIterator().remove() 触发", "[-d]", events.toString());
        events.clear();

        List<String> sub = list.subList(1, 3);
        equal("subList 视图可读", "[y, c]", sub.toString());
        sub.add("s1");
        equal("subList add 触发父 onAdd", "[+s1]", events.toString());
        events.clear();
        sub.set(0, "s0");
        equal("subList set 触发父 onReplace（绝对索引）", "[~1:y>s0]", events.toString());
        events.clear();
        sub.remove(1);
        equal("subList remove 触发父 onRemove", "[-c]", events.toString());
        events.clear();
        sub.clear();
        equal("subList clear 逐元素且无 onClear", "[-s0, -s1]", events.toString());
        equal("父列表随子视图清空", "[x]", list.toString());

        events.clear();
        list.addAll(List.of("p", "q", "r"));
        events.clear();
        List<String> nested = list.subList(1, 4).subList(1, 3);
        Iterator<String> nestedIterator = nested.iterator();
        nestedIterator.next();
        nestedIterator.remove();
        equal("嵌套 subList 迭代器 remove 经父触发", "[-q]", events.toString());
        equal("嵌套视图父列表落地", "[x, p, r]", list.toString());
    }

    private static void setEvents() {
        ObservableSet<String> set = ObservableSet.create();
        List<String> events = new ArrayList<>();
        set.onAdd(element -> events.add("+" + element));
        set.onRemove(element -> events.add("-" + element));
        set.onClear(() -> events.add("clear"));

        set.add("a");
        equal("set add 触发 onAdd", "[+a]", events.toString());
        set.add("a");
        equal("重复 add 不触发", "[+a]", events.toString());
        events.clear();
        set.addAll(List.of("b", "a", "c"));
        equal("addAll 只对新增触发", "[+b, +c]", events.toString());
        events.clear();
        set.remove("b");
        equal("remove 触发 onRemove", "[-b]", events.toString());
        set.remove("b");
        equal("remove 不存在不触发", "[-b]", events.toString());
        events.clear();
        set.removeIf(element -> element.equals("c"));
        equal("removeIf 触发", "[-c]", events.toString());
        events.clear();
        set.retainAll(List.of("a", "z"));
        equal("retainAll 无移除不触发", "[]", events.toString());
        events.clear();
        set.retainAll(List.of("z"));
        equal("retainAll 移除触发", "[-a]", events.toString());
        events.clear();
        set.add("k");
        set.add("m");
        events.clear();
        set.clear();
        equal("clear 逐元素后一次 onClear", "[-k, -m, clear]", events.toString());
        events.clear();

        set.add("j");
        events.clear();
        Iterator<String> iterator = set.iterator();
        iterator.next();
        iterator.remove();
        equal("iterator().remove() 触发 onRemove", "[-j]", events.toString());
    }

    private static void mapEvents() {
        ObservableMap<String, Integer> map = ObservableMap.create();
        List<String> events = new ArrayList<>();
        map.onAdd((key, value) -> events.add("+" + key + "=" + value));
        map.onUpdate((key, oldValue, newValue) -> events.add("~" + key + ":" + oldValue + ">" + newValue));
        map.onRemove((key, oldValue) -> events.add("-" + key + "=" + oldValue));
        map.onClear(() -> events.add("clear"));

        map.put("a", 1);
        equal("put 新键触发 onAdd", "[+a=1]", events.toString());
        map.put("a", 1);
        equal("put 相同值不触发", "[+a=1]", events.toString());
        map.put("a", 2);
        equal("put 覆盖触发 onUpdate", "[+a=1, ~a:1>2]", events.toString());
        events.clear();

        equal("put 返回旧值", Integer.valueOf(2), map.put("a", 3));
        events.clear();

        map.put("b", 9);
        map.remove("b");
        equal("remove 触发 onRemove 带旧值", "[+b=9, -b=9]", events.toString());
        events.clear();

        map.putIfAbsent("a", 5);
        equal("putIfAbsent 已存在不触发", "[]", events.toString());
        equal("putIfAbsent 已存在返回现值", Integer.valueOf(3), map.putIfAbsent("a", 5));
        map.putIfAbsent("c", 7);
        equal("putIfAbsent 新键触发 onAdd", "[+c=7]", events.toString());
        events.clear();

        map.compute("a", (key, value) -> null);
        equal("compute 映射为 null 触发 onRemove", "[-a=3]", events.toString());
        check("compute 删除后键不存在", !map.containsKey("a"));
        events.clear();

        map.put("d", 1);
        map.compute("d", (key, value) -> value + 10);
        equal("compute 更新触发 onUpdate", "[+d=1, ~d:1>11]", events.toString());
        events.clear();

        map.computeIfAbsent("e", key -> 42);
        equal("computeIfAbsent 新键触发 onAdd", "[+e=42]", events.toString());
        events.clear();
        map.computeIfAbsent("e", key -> 99);
        equal("computeIfAbsent 已存在不触发", "[]", events.toString());
        equal("computeIfAbsent 已存在不重算", Integer.valueOf(42), map.get("e"));
        events.clear();

        map.computeIfPresent("e", (key, value) -> null);
        equal("computeIfPresent 映射 null 触发 onRemove", "[-e=42]", events.toString());
        events.clear();

        map.merge("f", 1, Integer::sum);
        equal("merge 新键触发 onAdd", "[+f=1]", events.toString());
        events.clear();
        map.merge("f", 2, Integer::sum);
        equal("merge 重映射触发 onUpdate", "[~f:1>3]", events.toString());
        events.clear();
        map.merge("f", 5, (oldValue, value) -> null);
        equal("merge 重映射 null 触发 onRemove", "[-f=3]", events.toString());
        events.clear();

        map.put("g", 1);
        events.clear();
        map.replace("g", 4);
        equal("replace 更新触发 onUpdate", "[~g:1>4]", events.toString());
        events.clear();
        equal("replace 不存在的键返回 null", null, map.replace("zz", 4));
        equal("replace 不存在的键不触发", "[]", events.toString());
        map.replace("g", 4, 6);
        equal("replace(k,old,new) 成功触发 onUpdate", "[~g:4>6]", events.toString());
        equal("replace(k,old,new) 不匹配返回 false", false, map.replace("g", 999, 7));
        events.clear();

        map.put("h", 2);
        events.clear();
        map.remove("h", 2);
        equal("remove(k,v) 匹配触发 onRemove", "[-h=2]", events.toString());
        events.clear();
        map.put("h", 2);
        events.clear();
        map.remove("h", 5);
        equal("remove(k,v) 不匹配不触发", "[]", events.toString());
        events.clear();

        map.putAll(Map.of("i", 8));
        equal("putAll 逐条目触发", "[+i=8]", events.toString());
        events.clear();

        map.put("j", 5);
        events.clear();
        map.replaceAll((key, value) -> value * 2);
        equal("replaceAll 对每条目触发 onUpdate", "[~c:7>14, ~d:11>22, ~g:6>12, ~h:2>4, ~i:8>16, ~j:5>10]", events.toString());
        events.clear();

        map.put("k1", 1);
        map.put("k2", 2);
        events.clear();

        map.keySet().remove("k1");
        equal("keySet().remove 触发 onRemove", "[-k1=1]", events.toString());
        events.clear();

        map.values().remove(2);
        equal("values().remove 触发 onRemove 带键", "[-k2=2]", events.toString());
        events.clear();

        map.put("k3", 3);
        events.clear();

        Iterator<Map.Entry<String, Integer>> entryIterator = map.entrySet().iterator();
        entryIterator.next();
        entryIterator.remove();
        equal("entrySet 迭代器 remove 触发 onRemove", "[-c=14]", events.toString());
        events.clear();

        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (entry.getKey().equals("j")) {
                entry.setValue(20);
            }
        }
        equal("entry.setValue 触发 onUpdate", "[~j:10>20]", events.toString());
        events.clear();

        check("entrySet().remove 按条目等值删除", map.entrySet().remove(Map.entry("j", 20)));
        equal("entrySet().remove 触发 onRemove", "[-j=20]", events.toString());
        events.clear();

        map.keySet().removeIf(key -> key.equals("k3"));
        equal("keySet().removeIf 触发 onRemove", "[-k3=3]", events.toString());
        events.clear();

        map.put("v1", 5);
        map.put("v2", 6);
        events.clear();
        map.values().removeIf(value -> value.equals(5));
        equal("values().removeIf 触发 onRemove", "[-v1=5]", events.toString());
        events.clear();

        throwsExactly("keySet().add 被拒", UnsupportedOperationException.class, () -> map.keySet().add("nope"));
        throwsExactly("values().add 被拒", UnsupportedOperationException.class, () -> map.values().add(1));
        throwsExactly("entrySet().add 被拒", UnsupportedOperationException.class, () -> map.entrySet().add(Map.entry("nope", 1)));

        map.put("m1", 1);
        map.put("m2", 2);
        events.clear();
        map.clear();
        equal("clear 逐条目后一次 onClear", "[-d=22, -g=12, -h=4, -i=16, -v2=6, -m1=1, -m2=2, clear]", events.toString());
    }

    private static void subscriptions() {
        ObservableList<String> list = ObservableList.create();
        List<String> order = new ArrayList<>();
        Subscription first = list.onAdd(element -> order.add("first:" + element));
        Subscription second = list.onAdd(element -> order.add("second:" + element));
        Subscription third = list.onAdd(element -> order.add("third:" + element));
        list.add("a");
        equal("多监听器按注册顺序", "[first:a, second:a, third:a]", order.toString());
        check("isClosed 初值为假", !first.isClosed());
        second.close();
        list.add("b");
        equal("单个关闭不影响其余", "[first:a, second:a, third:a, first:b, third:b]", order.toString());
        second.close();
        equal("重复 close 不再注销", "[first:a, second:a, third:a, first:b, third:b]", order.toString());
        first.close();
        third.close();
        list.add("c");
        equal("全部关闭后无事件", "[first:a, second:a, third:a, first:b, third:b]", order.toString());
    }

    private static void reentrancy() {
        ObservableList<String> list = ObservableList.create();
        List<String> events = new ArrayList<>();
        list.onAdd(element -> events.add("+" + element));
        list.onRemove(element -> events.add("-" + element));
        boolean[] guard = {false};
        list.onAdd(element -> {
            if (!guard[0]) {
                guard[0] = true;
                list.add("auto");
            }
        });
        list.add("a");
        equal("监听器内再改列表安全且嵌套触发", "[+a, +auto]", events.toString());
        equal("重入修改落地", "[a, auto]", list.toString());

        events.clear();
        Subscription[] handle = new Subscription[1];
        handle[0] = list.onAdd(element -> {
            events.add("once:" + element);
            handle[0].close();
        });
        list.add("b");
        equal("监听器内关闭自身不再触发后续", "[+b, once:b]", events.toString());
        list.add("c");
        equal("关闭后其余监听器照常", "[+b, once:b, +c]", events.toString());

        ObservableMap<String, Integer> map = ObservableMap.create();
        List<String> mapEvents = new ArrayList<>();
        map.onAdd((key, value) -> mapEvents.add("+" + key + "=" + value));
        map.onAdd((key, value) -> {
            if (key.equals("seed") && !map.containsKey("grown")) {
                map.put("grown", value);
            }
        });
        map.put("seed", 7);
        equal("map 监听器内再 put 嵌套触发", "[+seed=7, +grown=7]", mapEvents.toString());
    }

    private static void contracts() {
        ObservableList<String> list = ObservableList.create();
        list.addAll(List.of("a", "b"));
        List<String> plain = List.of("a", "b");
        check("List equals 委托", list.equals(plain));
        equal("List hashCode 委托", plain.hashCode(), list.hashCode());
        equal("List toString 委托", "[a, b]", list.toString());

        ObservableSet<String> set = ObservableSet.create();
        set.addAll(List.of("a", "b"));
        Set<String> plainSet = new LinkedHashSet<>(List.of("a", "b"));
        check("Set equals 委托", set.equals(plainSet));
        equal("Set hashCode 委托", plainSet.hashCode(), set.hashCode());

        ObservableMap<String, Integer> map = ObservableMap.create();
        map.put("a", 1);
        Map<String, Integer> plainMap = Map.of("a", 1);
        check("Map equals 委托", map.equals(plainMap));
        equal("Map hashCode 委托", plainMap.hashCode(), map.hashCode());

        ObservableList<String> readOnly = list.unmodifiable();
        throwsExactly("不可变列表 add 被拒", UnsupportedOperationException.class, () -> readOnly.add("c"));
        throwsExactly("不可变列表 remove 被拒", UnsupportedOperationException.class, () -> readOnly.remove("a"));
        throwsExactly("不可变列表 set 被拒", UnsupportedOperationException.class, () -> readOnly.set(0, "z"));
        throwsExactly("不可变列表 clear 被拒", UnsupportedOperationException.class, readOnly::clear);
        throwsExactly("不可变列表迭代器 remove 被拒", UnsupportedOperationException.class, () -> {
            Iterator<String> iterator = readOnly.iterator();
            iterator.next();
            iterator.remove();
        });
        throwsExactly("不可变列表 subList 变更被拒", UnsupportedOperationException.class, () -> readOnly.subList(0, 1).clear());
        equal("不可变列表读透传", "[a, b]", readOnly.toString());

        List<String> viewEvents = new ArrayList<>();
        Subscription viewAdd = readOnly.onAdd(element -> viewEvents.add("+" + element));
        list.add("c");
        equal("源头变更经视图订阅透传", "[+c]", viewEvents.toString());
        viewAdd.close();

        ObservableSet<String> readOnlySet = set.unmodifiable();
        throwsExactly("不可变 set add 被拒", UnsupportedOperationException.class, () -> readOnlySet.add("z"));
        throwsExactly("不可变 set 迭代器 remove 被拒", UnsupportedOperationException.class, () -> {
            Iterator<String> iterator = readOnlySet.iterator();
            iterator.next();
            iterator.remove();
        });

        ObservableMap<String, Integer> readOnlyMap = map.unmodifiable();
        throwsExactly("不可变 map put 被拒", UnsupportedOperationException.class, () -> readOnlyMap.put("z", 1));
        throwsExactly("不可变 map remove 被拒", UnsupportedOperationException.class, () -> readOnlyMap.remove("a"));
        throwsExactly("不可变 map keySet remove 被拒", UnsupportedOperationException.class, () -> readOnlyMap.keySet().remove("a"));
        throwsExactly("不可变 map values remove 被拒", UnsupportedOperationException.class, () -> readOnlyMap.values().remove(1));
        throwsExactly("不可变 map entrySet 迭代器 remove 被拒", UnsupportedOperationException.class, () -> {
            Iterator<Map.Entry<String, Integer>> iterator = readOnlyMap.entrySet().iterator();
            iterator.next();
            iterator.remove();
        });
        throwsExactly("不可变 map entry setValue 被拒", UnsupportedOperationException.class, () -> {
            for (Map.Entry<String, Integer> entry : readOnlyMap.entrySet()) {
                entry.setValue(99);
            }
        });

        List<String> mapViewEvents = new ArrayList<>();
        Subscription mapViewAdd = readOnlyMap.onAdd((key, value) -> mapViewEvents.add("+" + key));
        map.put("d", 4);
        equal("不可变 map 订阅透传源头事件", "[+d]", mapViewEvents.toString());
        mapViewAdd.close();
    }

    private static void determinism() {
        ObservableSet<Integer> set = ObservableSet.create();
        set.addAll(List.of(5, 3, 9, 1));
        equal("LinkedHashSet 保插入序", "[5, 3, 9, 1]", set.toString());

        ObservableMap<String, Integer> map = ObservableMap.create();
        map.put("b", 2);
        map.put("a", 1);
        equal("LinkedHashMap 保插入序", "{b=2, a=1}", map.toString());

        ObservableList<String> list = ObservableList.over(new ArrayList<>(List.of("x")));
        list.add("y");
        equal("over 包装外部列表继续生效", "[x, y]", list.toString());
        check("over 与外部视图等值", list.equals(List.of("x", "y")));
    }

    private static void section(String title) {
        System.out.println(title);
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [ok] " + description);
        } else {
            FAILURES.add(description);
            System.out.println("  [FAIL] " + description);
        }
    }

    private static void equal(String description, Object expected, Object actual) {
        check(description + "（期望 " + expected + "，实际 " + actual + "）", Objects.equals(expected, actual));
    }

    private static void throwsExactly(String description, Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
            check(description, false);
        } catch (Throwable thrown) {
            check(description + "（期望 " + type.getSimpleName() + "，实际 " + thrown.getClass().getSimpleName() + "）", type.isInstance(thrown));
        }
    }

    private static void summary() {
        System.out.println();
        if (FAILURES.isEmpty()) {
            System.out.println("collection library self check: all " + passed + " checks passed.");
            return;
        }
        System.out.println("collection library self check: " + FAILURES.size() + " of " + (passed + FAILURES.size()) + " checks FAILED:");
        for (String failure : FAILURES) {
            System.out.println("  - " + failure);
        }
        throw new AssertionError(FAILURES.size() + " checks failed");
    }

}
