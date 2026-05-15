package com.cqie.deepcover.agent.push.interfaces;

import com.cqie.deepcover.agent.event.AgentEvent;

/**
 * 推送 Agent 事件的客户端接口。
 */
public interface AgentEventClient {
    void push(String roomCode, AgentEvent event);
}
