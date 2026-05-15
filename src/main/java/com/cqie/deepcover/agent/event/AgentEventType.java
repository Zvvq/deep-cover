package com.cqie.deepcover.agent.event;

/**
 * Java 推送给 Python Agent 的事件类型。
 */
public enum AgentEventType {
    ROOM_STARTED,
    CHAT_MESSAGE,
    VOTING_STARTED,
    PLAYER_ELIMINATED,
    ROUND_STARTED,
    GAME_ENDED,
    ROOM_DESTROYED
}
