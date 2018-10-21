package org.springframework.cloud.stream.app.websocket.processor;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.messaging.support.MessageBuilder;

public class WebsocketChannelInterceptor extends  ChannelInterceptorAdapter {
	
	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		if (message.getPayload() instanceof String) {
			return message;
		} else if (message.getPayload() instanceof byte[]) {
			return MessageBuilder.withPayload(new String((byte[]) message.getPayload())).build();
		}
		return message;
	};
}
