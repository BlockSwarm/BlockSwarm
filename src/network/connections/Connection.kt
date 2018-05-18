package blockswarm.network.connections

import blockswarm.network.cluster.Node
import blockswarm.network.cluster.NodeIncomingHandler
import blockswarm.network.cluster.PeerAddress
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.serialization.ClassResolvers
import io.netty.handler.codec.serialization.ObjectDecoder
import io.netty.handler.codec.serialization.ObjectEncoder
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import javax.net.ssl.SSLException

class Connection(pa: PeerAddress, internal var node: Node) {

    internal val HOST: String = pa.inetAddress().address.hostAddress
    internal val PORT: Int = pa.inetAddress().port
    private var SSL = false
    lateinit var channel: Channel

    init {
        setupConnection()
    }

    @Throws(SSLException::class, InterruptedException::class)
    private fun setupConnection() {
        // Configure SSL.
        val sslCtx: SslContext? = if (SSL) {
            SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build()
        } else {
            null
        }

        val group = NioEventLoopGroup()
        val b = Bootstrap()
        b.group(group)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    @Throws(Exception::class)
                    public override fun initChannel(ch: SocketChannel) {
                        val p = ch.pipeline()
                        if (sslCtx != null) {
                            p.addLast(sslCtx.newHandler(ch.alloc(), HOST, PORT))
                        }
                        p.addLast(
                                ObjectEncoder(),
                                ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                NodeIncomingHandler(node))
                    }
                })

        // Start the connection attempt.
        channel = b.connect(HOST, PORT).sync().channel()
    }

    fun send(message: Any) {
        channel.writeAndFlush(message)
    }

    fun shutdown() {
        channel.disconnect()
        channel.close()
    }
}