package com.cqie.deepcover.agent.event;

import java.time.Instant;

/**
 * Java 推给 Python Agent 的统一事件外壳。
 *
 * @param eventId 事件唯一 ID，Python 端可用来去重
 * @param type 事件类型
 * @param roomCode 房间号
 * @param createdAt Java 生成事件的时间
 * @param payload 事件内容，不同类型对应不同结构
 */
public record AgentEvent(
        String eventId,
        AgentEventType type,
        String roomCode,
        Instant createdAt,
        Object payload
) {
}
