package com.cqie.deepcover.agent.push.interfaces.impl;

import com.cqie.deepcover.agent.event.AgentEvent;
import com.cqie.deepcover.agent.push.interfaces.AgentEventClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 通过 HTTP 把 Java 游戏事件推给 Python Agent。
 */
@Component
public class HttpAgentEventClient implements AgentEventClient {
    private final RestClient restClient;
    private final String eventPath;
    private final String internalSecret;

    public HttpAgentEventClient(
            RestClient.Builder restClientBuilder,
            @Value("${deep-cover.agent.base-url:http://localhost:8000}") String baseUrl,
            @Value("${deep-cover.agent.event-path:/agent/rooms/{roomCode}/events}") String eventPath,
            @Value("${deep-cover.agent.internal-secret:dev-agent-secret}") String internalSecret
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.eventPath = eventPath;
        this.internalSecret = internalSecret;
    }

    @Override
    public void push(String roomCode, AgentEvent event) {
        restClient.post()
                .uri(eventPath, roomCode)
                .header("X-Internal-Agent-Secret", internalSecret)
                .body(event)
                .retrieve()
                .toBodilessEntity();
    }
}
