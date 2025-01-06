package top.sob.platform.api.models.kit.io

import org.jetbrains.annotations.Nullable
import top.sob.platform.api.models.kit.io.resource.Resource
import java.io.ObjectOutput
import java.io.OutputStream
import java.io.PrintWriter
import java.io.Writer
import java.net.URL
import java.nio.channels.WritableByteChannel
import java.nio.charset.Charset

interface OutputSource : ObjectOutput {

    fun toWritableChannel(): WritableByteChannel

    fun toOutputStream(): OutputStream

    fun toWriter(): Writer

    fun toPrinter(): PrintWriter {
        return PrintWriter(toWriter())
    }

    fun getCharset(): Charset

    fun setCharset(charset: Charset)

    @Nullable
    fun getResource(): Resource

    fun getURL(): URL

}