package org.springframework.cloud.stream.app.websocket.process.actuator;

import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.stream.app.websocket.processor.trace.InMemoryTraceRepository;
import org.springframework.cloud.stream.app.websocket.processor.trace.Trace;

@ConfigurationProperties(prefix = "endpoints.websocketprocessortrace")
@Endpoint(id = "websocketprocessortrace")
public class WebsocketProcessorTraceEndpoint {

	private static final Log logger = LogFactory.getLog(WebsocketProcessorTraceEndpoint.class);

	private boolean enabled;

	private final InMemoryTraceRepository repository;

	public WebsocketProcessorTraceEndpoint(InMemoryTraceRepository repository) {
		this.repository = repository;
		logger.info(String.format("/websocketprocessortrace enabled: %b", this.enabled));
	}

	@PostConstruct
	public void init() {
	}

	@ReadOperation
	public List<Trace> traces() {
		return this.repository.findAll();
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
