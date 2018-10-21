package org.springframework.cloud.stream.app.websocket.processor;

import io.netty.channel.Channel;

public class WebsocketChannelHolder {

	private Channel channel;
	private String deviceId;

	public WebsocketChannelHolder(Channel channel, String deviceId) {
		super();
		this.channel = channel;
		this.deviceId = deviceId;
	}
	
	public Channel getChannel() {
		return channel;
	}
	public String getDeviceId() {
		return deviceId;
	}	
	
}
