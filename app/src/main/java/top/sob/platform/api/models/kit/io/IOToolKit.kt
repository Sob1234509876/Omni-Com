package top.sob.platform.api.models.kit.io

import top.sob.platform.api.models.kit.ToolKit
import top.sob.platform.api.models.kit.io.autosave.AutoSaveToolKit
import top.sob.platform.api.models.kit.io.file.FileToolKit
import top.sob.platform.api.models.kit.io.net.NetToolKit
import top.sob.platform.api.models.kit.io.net.security.SecurityToolKit
import top.sob.platform.api.models.kit.io.resource.ResourceToolKit

//TODO: Finish this
interface IOToolKit : ToolKit {

    fun getAutoSaves(): AutoSaveToolKit

    fun getFiles(): FileToolKit

    fun getNetworks(): NetToolKit

    fun getSecurities(): SecurityToolKit {
        return getNetworks().getSecurities()
    }

    fun getResources(): ResourceToolKit

}