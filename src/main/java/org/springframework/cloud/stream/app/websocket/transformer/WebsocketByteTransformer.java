package org.springframework.cloud.stream.app.websocket.transformer;

import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WebsocketByteTransformer {
	private final Logger log = LoggerFactory.getLogger(WebsocketByteTransformer.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	public String toString(Object obj) {
		return makePayloadString(obj);
	}

	private String makePayloadString(Object obj) {
		if (obj instanceof String) {
			return (String) obj;
		} else if (obj instanceof LinkedHashMap) {
			try {
				return objectMapper.writeValueAsString(obj);
			} catch (JsonProcessingException e) {
				log.error("---- json conversion exception: " + e.getMessage());
				return null;
			}
		} else if (obj instanceof byte[]) {
			return new String((byte[]) obj);
		} else {
			return null;
		}
	}
}
