package top.sob.platform.api.misc

interface Executor<T> {
    fun execute(): T
}