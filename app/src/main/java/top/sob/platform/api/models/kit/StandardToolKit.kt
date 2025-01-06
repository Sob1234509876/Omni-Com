package top.sob.platform.api.models.kit

import top.sob.platform.api.models.kit.io.IOToolKit
import top.sob.platform.api.models.kit.io.autosave.AutoSaveToolKit
import top.sob.platform.api.models.kit.io.file.FileToolKit
import top.sob.platform.api.models.kit.io.net.NetToolKit
import top.sob.platform.api.models.kit.io.net.security.SecurityToolKit
import top.sob.platform.api.models.kit.io.resource.ResourceToolKit
import top.sob.platform.api.models.kit.logging.LoggingToolKit

import top.sob.platform.api.models.kit.math.MathToolKit
import top.sob.platform.api.models.kit.runtime.RuntimeToolKit
import top.sob.platform.api.models.kit.ui.UIToolKit
import top.sob.platform.api.models.kit.wrapper.WrapperToolKit

interface StandardToolKit : DevToolKit {

    fun getIOS(): IOToolKit

    fun getAutoSaves(): AutoSaveToolKit {
        return getIOS().getAutoSaves()
    }

    fun getFiles(): FileToolKit {
        return getIOS().getFiles()
    }

    fun getNetworks(): NetToolKit {
        return getIOS().getNetworks()
    }

    fun getSecurities(): SecurityToolKit {
        return getIOS().getSecurities()
    }

    fun getResources(): ResourceToolKit {
        return getIOS().getResources()
    }

    fun getMaths(): MathToolKit

    fun getRuntimes(): RuntimeToolKit

    fun getUIS(): UIToolKit

    fun getWrappers(): WrapperToolKit

    fun getLogging(): LoggingToolKit

}