package top.sob.platform.api.reflect

import top.sob.platform.api.Platform

interface Plugin : Runnable {

    fun init(args: Array<String>, platform: Platform)

}