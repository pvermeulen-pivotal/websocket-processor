package org.springframework.cloud.stream.app.websocket.processor;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.netty.handler.logging.LogLevel;

@ConfigurationProperties("websocket")
public class WebsocketProcessorProperties {

	public static final String DEFAULT_LOGLEVEL = LogLevel.WARN.toString();

	public static final String DEFAULT_PATH = "/websocket";

	public static final int DEFAULT_THREADS = 1;

	public static final int DEFAULT_PORT = 9292;

	public static final String DEFAULT_JSON_FIELD = "deviceId";

	/**
	 * whether or not to create a {@link io.netty.handler.ssl.SslContext}
	 */
	boolean ssl;

	/**
	 * the port on which the Netty server listens. Default is <tt>9292</tt>
	 */
	int port = DEFAULT_PORT;

	/**
	 * the number of threads for the Netty {@link io.netty.channel.EventLoopGroup}.
	 * Default is <tt>1</tt>
	 */
	int threads = DEFAULT_THREADS;

	/**
	 * the logLevel for netty channels. Default is <tt>WARN</tt>
	 */
	String logLevel = DEFAULT_LOGLEVEL;

	/**
	 * the path on which a WebsocketSink consumer needs to connect. Default is
	 * <tt>/websocket</tt>
	 */
	String path = DEFAULT_PATH;

	/**
	 * the name of the field in the JSON payload used for matching incoming and 
	 * outgoing request messages to proper channel. Default is
	 * <tt>deviceId</tt>
	 */
	String jsonFieldToMatch = DEFAULT_JSON_FIELD;

	public boolean isSsl() {
		return ssl;
	}

	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getThreads() {
		return threads;
	}

	public void setThreads(int threads) {
		this.threads = threads;
	}

	public String getLogLevel() {
		return logLevel;
	}

	public void setLogLevel(String logLevel) {
		this.logLevel = logLevel;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getJsonFieldToMatch() {
		return jsonFieldToMatch;
	}

	public void setJsonFieldToMatch(String jsonFieldToMatch) {
		this.jsonFieldToMatch = jsonFieldToMatch;
	}

}
