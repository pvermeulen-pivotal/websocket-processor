package org.springframework.cloud.stream.app.websocket.processor;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.cloud.stream.app.websocket.processor.trace.InMemoryTraceRepository;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;

class WebsocketProcessorServerHandler extends SimpleChannelInboundHandler<Object> {

	private static final Log logger = LogFactory.getLog(WebsocketProcessorServerHandler.class);

	private final boolean traceEnabled;

	private final InMemoryTraceRepository websocketTraceRepository;

	private final WebsocketProcessorConfiguration websocketProcessorConfiguration;

	private final WebsocketProcessorProperties properties;

	private WebSocketServerHandshaker handshaker;

	public WebsocketProcessorServerHandler(InMemoryTraceRepository websocketTraceRepository,
			WebsocketProcessorProperties properties, WebsocketProcessorConfiguration websocketProcessorConfiguration,
			boolean traceEnabled) {
		this.websocketProcessorConfiguration = websocketProcessorConfiguration;
		this.websocketTraceRepository = websocketTraceRepository;
		this.properties = properties;
		this.traceEnabled = traceEnabled;
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, Object msg) {
		if (msg instanceof FullHttpRequest) {
			handleHttpRequest(ctx, (FullHttpRequest) msg);
		} else if (msg instanceof WebSocketFrame) {
			handleWebSocketFrame(ctx, (WebSocketFrame) msg);
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
		// Handle a bad request.
		if (!req.decoderResult().isSuccess()) {
			logger.warn(String.format("Bad request: %s", req.uri()));
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
			return;
		}

		// Allow only GET methods.
		if (req.method() != GET) {
			logger.warn(String.format("Unsupported HTTP method: %s", req.method()));
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
			return;
		}

		// enable subclasses to do additional processing
		if (!additionalHttpRequestHandler(ctx, req)) {
			return;
		}

		// Handshake
		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req),
				null, true);

		this.handshaker = wsFactory.newHandshaker(req);
		if (this.handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
		} else {
			this.handshaker.handshake(ctx.channel(), req);
		}
	}

	private void removeClosedChannel(ChannelHandlerContext ctx) {
		for (int i = 0; i < WebsocketProcessorServer.channels.size(); i++) {
			if (WebsocketProcessorServer.channels.get(i).getChannel().equals(ctx.channel())) {
				WebsocketProcessorServer.channels.remove(i);
				break;
			}
		}
	}

	private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
		String errorMsg = null;
		// Check for closing frame
		if (frame instanceof CloseWebSocketFrame) {
			addTraceForFrame(frame, "close");
			this.handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
			removeClosedChannel(ctx);
			return;
		}

		// Check for ping
		if (frame instanceof PingWebSocketFrame) {
			addTraceForFrame(frame, "ping");
			ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
			return;
		}

		if (!(frame instanceof TextWebSocketFrame)) {
			throw new UnsupportedOperationException(
					String.format("%s frame types not supported", frame.getClass().getName()));
		}

		try {
			JSONObject json = new JSONObject(((TextWebSocketFrame) frame).text());
			String deviceId = (String) json.get(properties.getJsonFieldToMatch());
			String sPayload = ((TextWebSocketFrame) frame).text();
			if (!websocketProcessorConfiguration.processMessage(sPayload)) {
				ctx.channel().write(new TextWebSocketFrame("Unable to process request"));
			} else {
				WebsocketProcessorServer.channels.add(new WebsocketChannelHolder(ctx.channel(), deviceId));
			}
			return;
		} catch (Exception e) {
			errorMsg = "Exception receiving json request missing or invalid deviceId: " + e.getMessage();
			logger.error(errorMsg);
		}
		if (errorMsg != null)
			ctx.channel().write(new TextWebSocketFrame("Exception processing message error: " + errorMsg));		
	}

	private boolean additionalHttpRequestHandler(ChannelHandlerContext ctx, FullHttpRequest req) {
		return true;
	}

	// add trace information for received frame
	private void addTraceForFrame(WebSocketFrame frame, String type) {
		Map<String, Object> trace = new LinkedHashMap<>();
		trace.put("type", type);
		trace.put("direction", "in");
		if (frame instanceof TextWebSocketFrame) {
			trace.put("payload", ((TextWebSocketFrame) frame).text());
		}

		if (this.traceEnabled) {
			this.websocketTraceRepository.add(trace);
		}
	}

	private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
		// Generate an error page if response getStatus code is not OK (200).
		if (res.status().code() != 200) {
			ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
			res.content().writeBytes(buf);
			buf.release();
			HttpUtil.setContentLength(res, res.content().readableBytes());
		}

		// Send the response and close the connection if necessary.
		ChannelFuture f = ctx.channel().writeAndFlush(res);
		if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		logger.error("Websocket error", cause);
		cause.printStackTrace();
		ctx.close();
	}

	private String getWebSocketLocation(FullHttpRequest req) {
		String location = req.headers().get(HOST) + this.properties.getPath();
		if (this.properties.isSsl()) {
			return "wss://" + location;
		} else {
			return "ws://" + location;
		}
	}

}
