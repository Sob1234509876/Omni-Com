package top.sob.platform.api.reflect

import top.sob.platform.api.Platform

interface Script {

    fun run(args: Array<String>, platform: Platform)

}