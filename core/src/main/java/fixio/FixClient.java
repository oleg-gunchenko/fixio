/*
 * Copyright 2014 The FIX.io Project
 *
 * The FIX.io Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package fixio;

import fixio.fixprotocol.FixMessage;
import fixio.handlers.AdminEventHandler;
import fixio.handlers.FixApplicationAdapter;
import fixio.handlers.FixMessageHandler;
import fixio.netty.pipeline.client.FixInitiatorChannelInitializer;
import fixio.netty.pipeline.client.FixSessionSettingsProvider;
import fixio.netty.pipeline.client.PropertyFixSessionSettingsProviderImpl;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class FixClient extends AbstractFixConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(FixClient.class);
    private Channel channel;
    private EventLoopGroup bossEventLoopGroup;
    private EventLoopGroup workerEventLoopGroup;
    private FixSessionSettingsProvider sessionSettingsProvider;

    public FixClient(FixApplicationAdapter fixApplication) {
        this(fixApplication, fixApplication);
    }

    public FixClient(AdminEventHandler adminEventHandler, FixMessageHandler... appMessageHandlers) {
        super(adminEventHandler, appMessageHandlers);
    }

    /**
     * Initialize {@link #sessionSettingsProvider} with {@link PropertyFixSessionSettingsProviderImpl}
     * using specified property file resource.
     *
     * @param settingsResource property file location related to classpath.
     */
    public void setSettingsResource(String settingsResource) {
        this.sessionSettingsProvider = new PropertyFixSessionSettingsProviderImpl(settingsResource);
    }

    public void setSessionSettingsProvider(FixSessionSettingsProvider sessionSettingsProvider) {
        assert sessionSettingsProvider != null;
        this.sessionSettingsProvider = sessionSettingsProvider;
    }

    public ChannelFuture connect(int port) throws InterruptedException {
        return connect(new InetSocketAddress(port));
    }

    public ChannelFuture connect(SocketAddress serverAddress) throws InterruptedException {
        Bootstrap b = new Bootstrap();
        bossEventLoopGroup = new NioEventLoopGroup();
        workerEventLoopGroup = new NioEventLoopGroup(8);
        try {
            b.group(bossEventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .remoteAddress(serverAddress)
                    .option(ChannelOption.TCP_NODELAY,
                            Boolean.parseBoolean(System.getProperty(
                                    "nfs.rpc.tcp.nodelay", "true")))
                    .option(ChannelOption.ALLOCATOR, new PooledByteBufAllocator())
                    .handler(new FixInitiatorChannelInitializer<SocketChannel>(
                            workerEventLoopGroup,
                            sessionSettingsProvider,
                            getAdminHandler(),
                            getAppMessageHandlers()
                    ))
                    .validate();

            channel = b.connect().sync().channel();
            LOGGER.info("FixClient is started and connected to {}", channel.remoteAddress());
            return channel.closeFuture();
        } finally {
            // b.shutdown();
        }
    }

    public void disconnect() throws InterruptedException {
        LOGGER.info("Closing connection to {}", channel.remoteAddress());
        channel.close().sync();
        bossEventLoopGroup.shutdownGracefully();
        workerEventLoopGroup.shutdownGracefully();
        bossEventLoopGroup = null;
        workerEventLoopGroup = null;
    }

    public void send(FixMessage fixMessage) throws InterruptedException {
        channel.writeAndFlush(fixMessage);
    }
}
