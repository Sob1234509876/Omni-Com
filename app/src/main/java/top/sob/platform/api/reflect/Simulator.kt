package top.sob.platform.api.reflect

import top.sob.platform.api.EnvironmentHandler

interface Simulator {

    fun getClassLoader(): ClassLoader

    fun getEnvironment(): EnvironmentHandler

}