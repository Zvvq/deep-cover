package com.cqie.deepcover.agent.push.service;

import com.cqie.deepcover.agent.event.AgentChatMessagePayload;
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
            log.debug("Agent 事件推送开关关闭，跳过推送，roomCode={}, type={}", roomCode, type);
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
            if (payload instanceof AgentChatMessagePayload messagePayload) {
                log.info("推送 Agent 聊天消息事件成功，roomCode={}, type={}, eventId={}, messageId={}, senderPlayerId={}, content={}",
                        roomCode, type, event.eventId(), messagePayload.messageId(), messagePayload.senderPlayerId(), messagePayload.content());
            } else {
                log.info("推送 Agent 事件成功，roomCode={}, type={}, eventId={}", roomCode, type, event.eventId());
            }
        } catch (RuntimeException ex) {
            log.warn("推送 Agent 事件失败，Java 游戏流程继续执行，roomCode={}, type={}, eventId={}, exceptionType={}, message={}",
                    roomCode, type, event.eventId(), ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}
