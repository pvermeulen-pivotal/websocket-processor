package org.springframework.cloud.stream.app.websocket.processor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.annotation.Autowired;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class WebsocketProcessorServer {

	private static final Log logger = LogFactory.getLog(WebsocketProcessorServer.class);

	static final List<WebsocketChannelHolder> channels = Collections.synchronizedList(new ArrayList<WebsocketChannelHolder>());

	@Autowired
	WebsocketProcessorProperties properties;

	@Autowired
	WebsocketProcessorServerInitializer initializer;

	private EventLoopGroup bossGroup;

	private EventLoopGroup workerGroup;

	private int port;

	public int getPort() {
		return this.port;
	}

	@PostConstruct
	public void init() {
		bossGroup = new NioEventLoopGroup(properties.getThreads());
		workerGroup = new NioEventLoopGroup();
	}

	@PreDestroy
	public void shutdown() {
		bossGroup.shutdownGracefully();
		workerGroup.shutdownGracefully();
	}

	public void run() throws InterruptedException {
		NioServerSocketChannel channel = (NioServerSocketChannel) new ServerBootstrap().group(bossGroup, workerGroup)
			.channel(NioServerSocketChannel.class)
			.handler(new LoggingHandler(nettyLogLevel()))
			.childHandler(initializer)
			.bind(properties.getPort())
			.sync()
			.channel();
		this.port = channel.localAddress().getPort();
		dumpProperties();
	}

	private void dumpProperties() {
		logger.info("███████████████████████████████████████████████████████████");
		logger.info("                >> websocket-processor config <<                ");
		logger.info("");
		logger.info(String.format("port:     %s", this.port));
		logger.info(String.format("ssl:               %s", this.properties.isSsl()));
		logger.info(String.format("path:     %s", this.properties.getPath()));
		logger.info(String.format("logLevel: %s", this.properties.getLogLevel()));
		logger.info(String.format("threads:           %s", this.properties.getThreads()));
		logger.info("");
		logger.info("████████████████████████████████████████████████████████████");
	}

	private LogLevel nettyLogLevel() {
		return LogLevel.valueOf(properties.getLogLevel().toUpperCase());
	}


}
