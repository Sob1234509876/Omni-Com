package top.sob.platform.api

import top.sob.platform.api.misc.Builder
import top.sob.platform.api.models.kit.StandardToolKit
import top.sob.platform.impl.PlatformImpl
import top.sob.platform.proof.Modifiable
import java.net.URI

class PlatformBuilder : Builder<Platform> {

    private lateinit var kit: StandardToolKit

    private var cp = HashSet<URI>()

    fun setToolKit(kit: StandardToolKit): PlatformBuilder {
        this.kit = kit
        return this
    }

    @Modifiable
    fun getClassPath(): Set<URI> {
        return cp
    }

    override fun build(): Platform {
        return PlatformImpl(cp, kit)
    }
}