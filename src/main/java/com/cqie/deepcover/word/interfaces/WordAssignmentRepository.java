package com.cqie.deepcover.word.interfaces;

import com.cqie.deepcover.word.record.WordAssignment;

import java.util.List;
import java.util.Optional;

/**
 * 玩家关键词分配仓库接口。
 *
 * <p>当前先按房间保存最新一轮分配结果，后续做多轮历史时可以扩展查询方法。</p>
 */
public interface WordAssignmentRepository {
    void saveAll(String roomCode, int roundNumber, List<WordAssignment> assignments);

    Optional<WordAssignment> findLatestByRoomCodeAndPlayerId(String roomCode, String playerId);

    Optional<Integer> findLatestRoundNumber(String roomCode);

    void deleteByRoomCode(String roomCode);
}
