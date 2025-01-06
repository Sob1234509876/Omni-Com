package top.sob.platform.api.models.kit.io.net

import top.sob.platform.api.misc.model.Strategic
import top.sob.platform.api.models.kit.ToolKit
import top.sob.platform.api.models.kit.io.net.security.SecurityToolKit
import java.net.InetAddress
import java.net.SocketAddress
import java.nio.ByteBuffer

interface NetToolKit : ToolKit, Strategic {

    fun getSecurities(): SecurityToolKit

    fun freePort(): Int

    fun getLocalAddress(): InetAddress

    fun getAddress(s: String): InetAddress

    fun send(bytes: ByteBuffer, sa: SocketAddress): Int

    fun rec(bytes: ByteBuffer, sa: SocketAddress): Int

    enum class Strategy {
        BY_STREAM,
        BY_PACKET,
        BY_PACKETS
    }

}