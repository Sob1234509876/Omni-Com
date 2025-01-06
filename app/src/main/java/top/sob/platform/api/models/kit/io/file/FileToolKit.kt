package top.sob.platform.api.models.kit.io.file

import top.sob.platform.api.models.kit.ToolKit
import top.sob.platform.api.models.kit.io.IOSource
import java.nio.file.Path

interface FileToolKit : ToolKit {
    fun mkDirs(p: Path)

    fun createTemporary(p: Array<String>)

    fun openSource(p: Path): IOSource
}