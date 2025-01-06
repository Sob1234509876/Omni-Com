package top.sob.platform.api.models.kit.io.net.security

import top.sob.platform.api.models.auth.Authorizer
import top.sob.platform.api.models.kit.ToolKit
import java.util.function.Predicate

interface SecurityToolKit : ToolKit {
    fun getAuthorizer(): Authorizer

    /**
     *
     * Gets the last class that is being invoked through reflection.
     *
     * @return The reflected class
     * @see SecurityToolKit.getReflector
     * @since 1.3.0a
     * */
    fun getReflected(): Class<*>

    /**
     *
     * Gets the last class that is invoking methods using reflection.
     *
     * @return The reflector of the class
     * @see SecurityToolKit.getReflected
     *
     * @since 1.3.0a
     * */
    fun getReflector(): Class<*>

    fun requireNonSpecInvoker(p: Predicate<Class<*>>) {
        if (p.test(getReflector()))
            throw SecurityException()
    }

    fun requireNonScriptInvoker()

    fun requireNonPluginInvoker()

    fun isSpecInvoker(p: Predicate<Class<*>>): Boolean {
        return p.test(getReflector())
    }

    fun isScriptInvoker()

    fun isPluginInvoker()

}