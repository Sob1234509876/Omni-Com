package top.sob.platform.api.reflect

import top.sob.platform.api.misc.Wrapper

interface PluginWrapper : Wrapper<Plugin>, Simulator {

    fun getMeta(): PluginMeta

}