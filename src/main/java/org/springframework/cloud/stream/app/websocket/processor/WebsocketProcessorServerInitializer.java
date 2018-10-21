package org.springframework.cloud.stream.app.websocket.processor;

import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.app.websocket.processor.trace.InMemoryTraceRepository;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

public class WebsocketProcessorServerInitializer extends ChannelInitializer<SocketChannel> {

	public static final int MAX_CONTENT_LENGTH = 65536;

	private final InMemoryTraceRepository traceRepository;

	@Autowired
	private WebsocketProcessorProperties properties;

	@Value("${endpoints.websocketsinktrace.enabled:false}")
	private boolean traceEnabled;

	@Autowired
	private WebsocketProcessorConfiguration websocketProcessorConfiguration;

	public WebsocketProcessorServerInitializer(InMemoryTraceRepository traceRepository) {
		this.traceRepository = traceRepository;
	}

	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();

		final SslContext sslCtx = configureSslContext();
		if (sslCtx != null) {
			pipeline.addLast(sslCtx.newHandler(ch.alloc()));
		}

		pipeline.addLast(new HttpServerCodec());
		pipeline.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
		pipeline.addLast(new WebsocketProcessorServerHandler(this.traceRepository, this.properties,
				this.websocketProcessorConfiguration, this.traceEnabled));
	}

	private SslContext configureSslContext() throws CertificateException, SSLException {
		if (this.properties.isSsl()) {
			SelfSignedCertificate ssc = new SelfSignedCertificate();
			return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
		} else {
			return null;
		}
	}

}
