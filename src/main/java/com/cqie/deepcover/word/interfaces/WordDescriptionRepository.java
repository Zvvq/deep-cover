package com.cqie.deepcover.word.interfaces;

import com.cqie.deepcover.word.record.WordDescription;

import java.util.List;
import java.util.Optional;

/**
 * 关键词描述记录仓库接口。
 */
public interface WordDescriptionRepository {
    void save(WordDescription description);

    List<WordDescription> findByRoomCodeAndRound(String roomCode, int roundNumber);

    Optional<WordDescription> findByRoomCodeAndRoundAndPlayerId(String roomCode, int roundNumber, String playerId);

    void deleteByRoomCode(String roomCode);
}
