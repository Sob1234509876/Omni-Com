package top.sob.platform.api.models.kit

interface DevToolKit : ToolKit {

    fun forName(s: String): ToolKit

    fun valueOf(s: String): ToolKit {
        return forName(s)
    }

    companion object {

        private val INSTANCE = StandardToolKitImpl.getInstance()

        fun getDefault(): StandardToolKit {
            return INSTANCE
        }
    }

}