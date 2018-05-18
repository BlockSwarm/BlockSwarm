package blockswarm.network.connections

import blockswarm.network.cluster.Node
import blockswarm.network.cluster.NodeIncomingHandler
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.serialization.ClassResolvers
import io.netty.handler.codec.serialization.ObjectDecoder
import io.netty.handler.codec.serialization.ObjectEncoder
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.SelfSignedCertificate
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

class Server(private val PORT: Int, private val node: Node) : Runnable {
    private var SSL = false

    init {
        Thread(this, "Server Thread").start()
    }

    @Throws(CertificateException::class, SSLException::class, InterruptedException::class)
    private fun setupServer() {
        // Configure SSL.
        val sslCtx: SslContext?
        sslCtx = if (SSL) {
            val ssc = SelfSignedCertificate()
            SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build()
        } else {
            null
        }

        val bossGroup = NioEventLoopGroup(1)
        val workerGroup = NioEventLoopGroup()
        try {
            val b = ServerBootstrap()
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel::class.java)
                    .handler(LoggingHandler(LogLevel.INFO))
                    .childHandler(object : ChannelInitializer<SocketChannel>() {
                        @Throws(Exception::class)
                        public override fun initChannel(ch: SocketChannel) {
                            val p = ch.pipeline()
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc()))
                            }
                            p.addLast(
                                    ObjectEncoder(),
                                    ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                                    NodeIncomingHandler(node))
                        }
                    })

            // Bind and start to accept incoming connections.
            b.bind(PORT).sync().channel().closeFuture().sync()
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }

    override fun run() {
        try {
            setupServer()
        } catch (e: CertificateException) {
            e.printStackTrace()
        } catch (e: SSLException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}