package top.sob.platform.api.models.kit.io

import org.jetbrains.annotations.Nullable
import top.sob.platform.api.models.kit.io.resource.Resource
import java.io.InputStream
import java.io.ObjectInput
import java.io.Reader
import java.net.URL
import java.nio.channels.ReadableByteChannel
import java.nio.charset.Charset
import java.util.Scanner

interface InputSource : ObjectInput {

    fun toReadableChannel(): ReadableByteChannel

    fun toInputStream(): InputStream

    fun toReader(): Reader

    fun toScanner(): Scanner {
        return Scanner(toReader())
    }

    fun getCharset(): Charset

    fun setCharset(charset: Charset)

    @Nullable
    fun getResource(): Resource

    fun getURL(): URL

}