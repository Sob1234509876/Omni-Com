package top.sob.platform.api.models.kit.io.autosave

import top.sob.platform.api.models.kit.ToolKit
import java.nio.file.Path
import java.util.LinkedList

interface AutoSaveToolKit : ToolKit {

    fun create(p: Path): AutoSaver

    fun getAutoSaved(p: Path): LinkedList<*>

}