package top.sob.platform.api.misc

import java.util.Properties

interface Wrapper<T> {

    fun getBody(): T

    fun getHeader(): Properties

}