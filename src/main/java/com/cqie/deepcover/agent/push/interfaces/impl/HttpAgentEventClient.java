package com.cqie.deepcover.agent.push.interfaces.impl;

import com.cqie.deepcover.agent.event.AgentEvent;
import com.cqie.deepcover.agent.push.interfaces.AgentEventClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通过 HTTP 把 Java 游戏事件推给 Python Agent。
 */
@Component
public class HttpAgentEventClient implements AgentEventClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String eventPath;
    private final String internalSecret;

    public HttpAgentEventClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${deep-cover.agent.base-url:http://localhost:8000}") String baseUrl,
            @Value("${deep-cover.agent.event-path:/agent/rooms/{roomCode}/events}") String eventPath,
            @Value("${deep-cover.agent.internal-secret:dev-agent-secret}") String internalSecret
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.eventPath = eventPath;
        this.internalSecret = internalSecret;
    }

    @Override
    public void push(String roomCode, AgentEvent event) {
        restClient.post()
                .uri(eventPath, roomCode)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Agent-Secret", internalSecret)
                .body(toJson(event))
                .retrieve()
                .toBodilessEntity();
    }

    private String toJson(AgentEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize agent event.", ex);
        }
    }
}
