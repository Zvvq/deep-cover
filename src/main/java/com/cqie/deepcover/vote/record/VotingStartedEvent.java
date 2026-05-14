package com.cqie.deepcover.vote.record;

/**
 * Spring 内部事件：投票阶段开始后，让 AI 投票模块有机会提交 AI 票。
 */
public record VotingStartedEvent(String roomCode, int roundNumber) {
}
