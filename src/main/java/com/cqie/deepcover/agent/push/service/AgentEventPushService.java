package com.cqie.deepcover.agent.push.service;

import com.cqie.deepcover.agent.event.AgentEvent;
import com.cqie.deepcover.agent.event.AgentEventType;
import com.cqie.deepcover.agent.push.interfaces.AgentEventClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Agent 事件推送服务。
 *
 * <p>游戏主流程不能因为 Python Agent 不可用而失败，所以这里会吞掉推送异常。</p>
 */
@Service
public class AgentEventPushService {
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
        } catch (RuntimeException ignored) {
            // Python Agent 服务暂时不可用时，Java 游戏流程继续。
        }
    }
}
