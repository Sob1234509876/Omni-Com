package top.sob.platform.api.models.kit

import top.sob.platform.api.models.kit.io.IOToolKit
import top.sob.platform.api.models.kit.logging.LoggingToolKit
import top.sob.platform.api.models.kit.math.MathToolKit
import top.sob.platform.api.models.kit.runtime.RuntimeToolKit
import top.sob.platform.api.models.kit.ui.UIToolKit
import top.sob.platform.api.models.kit.wrapper.WrapperToolKit

class StandardToolKitImpl : StandardToolKit {

    private constructor()

    override fun forName(s: String): ToolKit {
        TODO("Not yet implemented")
    }

    override fun getName(): String {
        TODO("Not yet implemented")
    }

    override fun getIOS(): IOToolKit {
        TODO("Not yet implemented")
    }

    override fun getMaths(): MathToolKit {
        TODO("Not yet implemented")
    }

    override fun getRuntimes(): RuntimeToolKit {
        TODO("Not yet implemented")
    }

    override fun getUIS(): UIToolKit {
        TODO("Not yet implemented")
    }

    override fun getWrappers(): WrapperToolKit {
        TODO("Not yet implemented")
    }

    override fun getLogging(): LoggingToolKit {
        TODO("Not yet implemented")
    }

    companion object {
        private val INSTANCE = StandardToolKitImpl()

        fun getInstance(): StandardToolKit {
            return INSTANCE
        }

    }
}