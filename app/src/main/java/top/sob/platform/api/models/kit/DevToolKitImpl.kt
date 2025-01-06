package top.sob.platform.api.models.kit

class DevToolKitImpl : DevToolKit {

    override fun forName(s: String): ToolKit {
        TODO("Not yet implemented")
    }

    private constructor()

    override fun getName(): String {
        TODO("Not yet implemented")
    }

    companion object {
        private val INSTANCE = DevToolKitImpl()

        fun getInstance(): DevToolKitImpl {
            return INSTANCE
        }
    }

}