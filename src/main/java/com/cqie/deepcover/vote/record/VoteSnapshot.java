package com.cqie.deepcover.vote.record;

/**
 * 投票阶段对外暴露的当前状态。
 *
 * @param roomCode 房间号
 * @param roundNumber 当前轮次
 * @param requiredVoteCount 本轮需要的票数，也就是存活玩家数量
 * @param submittedVoteCount 已提交票数
 * @param currentPlayerVoted 当前请求玩家是否已经投票
 */
public record VoteSnapshot(
        String roomCode,
        int roundNumber,
        int requiredVoteCount,
        int submittedVoteCount,
        boolean currentPlayerVoted
) {
}
