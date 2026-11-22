[English](README.md) | 简体中文

# Omni Com 集合库

Java 集合框架的可观察扩展，也是基于 Spring Boot 的 Omni Com 引擎重写的基石：`Observable<T>` 是标量值
持有者，`ObservableCollection<E>`、`ObservableSet<E>`、`ObservableList<E>` 与 `ObservableMap<K,V>` 扩展
各自的 JDK 对应类型，并为每一次真实变更触发事件。这个库是任意 `java.util` 底层集合之上的一层薄装饰器；
自检把每一处有文档记载的行为都固定下来。

## 坐标

| | |
| --- | --- |
| Gradle 路径 | `:projects:oc:oc-collection-library` |
| Java 模块 | `oc.projects.oc.collection.library` |
| 基础包 | `io.github.sob1234509876.oc.collection` |
| 组 / 版本 | `io.github.sob1234509876.oc` / `1.0a` |
| 构建 | `./gradlew :projects:oc:oc-collection-library:build`（编译期依赖缓存后即可离线构建） |
| 自检 | `./gradlew :projects:oc:oc-collection-library:selfcheck` |

## 它提供什么

零运行时依赖；JetBrains annotations 与 Lombok 仅为编译期。

- `Observable<T>` —— `create(T)` / `get()` / `set(T)` / `onChange(旧值, 新值)`；
- `ObservableCollection<E>` —— `onAdd(Consumer)` / `onRemove(Consumer)` / `onClear(Runnable)`；
- `ObservableList<E>` 增加 `onReplace(index, 旧元素, 新元素)`；
- `ObservableMap<K,V>` 增加 `onAdd(键, 值)`、`onUpdate(键, 旧值, 新值)`、`onRemove(键, 旧值)` 与
  `onClear(Runnable)`；
- 每个钩子都返回一个 `Subscription`（`AutoCloseable`，`close()` 幂等，`isClosed()`）；
- 静态工厂 `create()`（以 `ArrayList` / `LinkedHashSet` / `LinkedHashMap` 为底座，因此迭代顺序确定）与
  `over(...)`（包装既有集合——底层集合须保持私有，绕过包装器的变更不触发事件）；
- 每个类型还提供 `unmodifiable()`，返回只读视图。

## 事件

1. 事件是同步的：它们在执行变更的线程上、变更应用完成之后触发（订阅者看到的是最终状态）。
2. 监听器遍历的是注册表的快照：在监听器内部再变更同一个集合是安全且确定的（嵌套事件立即触发、深度优先）；
   在自己的回调内部关闭某个订阅也是安全的。
3. 同一个钩子上的监听器按注册顺序触发。
4. 批量操作按遇到顺序对每个元素各触发一个事件；没有单独的批量事件。`clear()` 先按遇到顺序对每个被移除的
   元素触发 `onRemove`，然后触发一次 `onClear`；清空空集合不触发任何事件。
5. 只有真实变更才触发：添加集合已有的元素、以相等的值 `put`、以相等的元素 `set`，都保持沉默。
6. `equals`、`hashCode` 与 `toString` 委托给底层集合，因此 `List`/`Set`/`Map` 契约原样成立。
7. 通过 `subList()` 进行的变更会被路由到父列表，订阅者收到的事件携带父列表的绝对索引；嵌套子列表同理。
   在 `sort`/`replaceAll` 期间从监听器内部做结构性变更，可能缩短预先计算好的目标位置并抛出异常——结果
   仍然是确定的。
8. 这些实现不是线程安全的：引擎使用单一世界线程，调用方必须在该线程上做变更。
9. `unmodifiable()` 视图对每一次变更都抛出 `UnsupportedOperationException`，但通过原可观察对象做出的
   变更仍会在视图的订阅上触发事件。

这些装饰器拦截每一条变更路径，包括普通包装器通常会漏掉的那些：`Iterator.remove`、`ListIterator.set/add`、
`subList`、映射的 `keySet()`/`values()`/`entrySet()` 视图（迭代器删除、`Entry.setValue`），以及整个
`putIfAbsent`/`compute`/`computeIfAbsent`/`computeIfPresent`/`merge`/`replace`/`remove(key,value)`/
`putAll`/`replaceAll` 家族——它们触发的事件与对应的直接操作完全相同。

## 自检

自检是一个可运行的程序（无测试框架），位于 `selfCheck` 源集。运行方式：
`./gradlew :projects:oc:oc-collection-library:selfcheck`。它覆盖上述事件契约、每一条泄漏路径、订阅语义
（幂等 `close`、顺序）、重入变更、视图契约（`equals`/`hashCode`/`toString` 委托、只读拒绝与事件透传）
以及确定的迭代顺序。
