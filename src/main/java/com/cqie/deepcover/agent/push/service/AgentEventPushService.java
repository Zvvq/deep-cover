package com.cqie.deepcover.agent.push.service;

import com.cqie.deepcover.agent.event.AgentEvent;
import com.cqie.deepcover.agent.event.AgentEventType;
import com.cqie.deepcover.agent.push.interfaces.AgentEventClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Pushes Java game events to the Python Agent Runtime.
 */
@Service
public class AgentEventPushService {
    private static final Logger log = LoggerFactory.getLogger(AgentEventPushService.class);

    private final AgentEventClient agentEventClient;
    private final boolean enabled;

    public AgentEventPushService(
            AgentEventClient agentEventClient,
            @Value("${deep-cover.agent.enabled:false}") boolean enabled
    ) {
        this.agentEventClient = agentEventClient;
        this.enabled = enabled;
    }

    public void push(String roomCode, AgentEventType type, Object payload) {
        if (!enabled) {
            log.debug("Skip agent event push because deep-cover.agent.enabled=false, roomCode={}, type={}", roomCode, type);
            return;
        }
        AgentEvent event = new AgentEvent(
                "agent-event-" + UUID.randomUUID(),
                type,
                roomCode,
                Instant.now(),
                payload
        );
        try {
            agentEventClient.push(roomCode, event);
        } catch (RuntimeException ex) {
            log.warn("Failed to push agent event to Python, roomCode={}, type={}, eventId={}", roomCode, type, event.eventId(), ex);
            // Python Agent service must not block the Java game flow.
        }
    }
}