package top.sob.platform.api

import org.jetbrains.annotations.Unmodifiable
import top.sob.platform.api.models.kit.StandardToolKit
import top.sob.platform.api.reflect.PluginWrapper
import top.sob.platform.api.reflect.ScriptWrapper
import java.net.URI
import java.net.URL

interface Platform {

    fun builder(): PlatformBuilder

    fun executor(): PlatformExecutor

    fun getToolKits(): StandardToolKit

    fun getPlugins(): @Unmodifiable Set<PluginWrapper>

    fun getScripts(): @Unmodifiable Set<ScriptWrapper>

    fun url(uri: URI): URL

    fun url(string: String): URL

}