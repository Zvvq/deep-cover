package com.cqie.deepcover.vote.record;

import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.vote.enums.GameWinner;

/**
 * 投票提交或结算后的结果。
 *
 * @param settled 本次调用是否已经触发结算
 * @param eliminatedPlayerId 被淘汰玩家；未结算时为 null
 * @param winner 游戏胜利方；游戏未结束时为 null
 */
public record VoteResult(
        String roomCode,
        int roundNumber,
        boolean settled,
        String eliminatedPlayerId,
        PlayerType eliminatedPlayerType,
        GameWinner winner
) {
    public static VoteResult notSettled(String roomCode, int roundNumber) {
        return new VoteResult(roomCode, roundNumber, false, null, null, null);
    }
}
