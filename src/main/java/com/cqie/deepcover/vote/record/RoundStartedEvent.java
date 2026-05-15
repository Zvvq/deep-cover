package com.cqie.deepcover.vote.record;

/**
 * Spring 内部事件：新一轮聊天阶段开始。
 */
public record RoundStartedEvent(String roomCode, int roundNumber) {
}
