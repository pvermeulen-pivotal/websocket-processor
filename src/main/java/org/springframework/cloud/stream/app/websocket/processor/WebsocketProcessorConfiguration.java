package org.springframework.cloud.stream.app.websocket.processor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.app.websocket.process.actuator.WebsocketProcessorTraceEndpoint;
import org.springframework.cloud.stream.app.websocket.processor.trace.InMemoryTraceRepository;
import org.springframework.cloud.stream.app.websocket.transformer.WebsocketByteTransformer;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.router.PayloadTypeRouter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.InterceptableChannel;
import org.springframework.messaging.support.MessageBuilder;

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

@EnableBinding(Processor.class)
@EnableConfigurationProperties(WebsocketProcessorProperties.class)
public class WebsocketProcessorConfiguration {

	private static final Log logger = LogFactory.getLog(WebsocketProcessorConfiguration.class);

	private final InMemoryTraceRepository websocketTraceRepository = new InMemoryTraceRepository();

	@Value("${endpoints.websocketprocessortrace.enabled:false}")
	private boolean traceEndpointEnabled;

	@Value("${websocket.jsonFieldToMatch:deviceId}")
	private String jsonFieldToMatch;

	@PostConstruct
	public void init() throws InterruptedException {
		server().run();
	}

	@Bean
	public WebsocketProcessorServer server() {
		return new WebsocketProcessorServer();
	}

	@Bean
	public WebsocketProcessorServerInitializer initializer() {
		return new WebsocketProcessorServerInitializer(this.websocketTraceRepository);
	}

	@Bean
	@ConditionalOnProperty(value = "endpoints.websocketprocessortrace.enabled", havingValue = "true")
	public WebsocketProcessorTraceEndpoint websocketTraceEndpoint() {
		return new WebsocketProcessorTraceEndpoint(this.websocketTraceRepository);
	}

	@ServiceActivator(inputChannel = Sink.INPUT)
	public void websocketSink(Message<?> message) {
		if (logger.isTraceEnabled()) {
			logger.trace("Handling message: " + message);
		}

		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);
		headers.setMessageTypeIfNotSet(SimpMessageType.MESSAGE);
		String messagePayload = new String((byte[]) message.getPayload());
		for (int i = 0; i < WebsocketProcessorServer.channels.size(); i++) {
			WebsocketChannelHolder channel = WebsocketProcessorServer.channels.get(i);

			if (logger.isTraceEnabled()) {
				logger.trace(String.format("Writing message %s to channel %s", messagePayload,
						channel.getChannel().localAddress()));
			}
			try {
				JSONObject json = new JSONObject(messagePayload);
				String deviceId = (String) json.get(jsonFieldToMatch);
				if (channel.getDeviceId().equals(deviceId)) {
					channel.getChannel().write(new TextWebSocketFrame(messagePayload));
					channel.getChannel().flush();
					WebsocketProcessorServer.channels.remove(i);
				}
			} catch (JSONException e) {
				logger.error("Exception sending response to channel: " + channel.getChannel().id() + " deviceId: "
						+ channel.getDeviceId() + " exception" + e.getMessage());
			}
		}

		if (this.traceEndpointEnabled) {
			addMessageToTraceRepository(message);
		}
	}

	private void addMessageToTraceRepository(Message<?> message) {
		Map<String, Object> trace = new LinkedHashMap<>();
		trace.put("type", "text");
		trace.put("direction", "out");
		trace.put("id", message.getHeaders().getId());
		trace.put("payload", message.getPayload().toString());
		this.websocketTraceRepository.add(trace);

	}

	@Autowired
	private MessageChannel output;

	@Bean
	public MessageChannel routerChannel() {
		return new DirectChannel();
	}

	@Bean
	public MessageChannel convertToStringChannel() {
		return new DirectChannel();
	}

	@Bean
	PayloadTypeRouter payloadTypeRouter() {
		PayloadTypeRouter router = new PayloadTypeRouter();
		router.setDefaultOutputChannel(output);
		router.setChannelMapping(Byte[].class.getName(), "convertToStringChannel");
		return router;
	}

	@Bean
	public IntegrationFlow startFlow() {
		return IntegrationFlows.from(routerChannel()).route(payloadTypeRouter()).get();
	}

	@Bean
	WebsocketByteTransformer transformer() {
		return new WebsocketByteTransformer();
	}

	@Bean
	IntegrationFlow convertToString() {
		return IntegrationFlows.from(convertToStringChannel()).transform(transformer(), "toString").channel(output)
				.get();
	}

	public boolean processMessage(Object obj) {
		if (obj instanceof Message) {
			return output.send((Message<?>) obj);
		} else {
			return output.send(MessageBuilder.withPayload(obj).build());
		}
	}

}
