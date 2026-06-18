package com.cqie.deepcover.word.interfaces.impl;

import com.cqie.deepcover.word.interfaces.WordAssignmentRepository;
import com.cqie.deepcover.word.record.WordAssignment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存关键词分配仓库。
 *
 * <p>latestRoundByRoom 记录房间当前轮次，assignments 按 roomCode/round/playerId 保存玩家拿到的词。</p>
 */
@Repository
@ConditionalOnProperty(name = "deep-cover.word-assignment.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryWordAssignmentRepository implements WordAssignmentRepository {
    private final Map<String, Integer> latestRoundByRoom = new ConcurrentHashMap<>();
    private final Map<String, WordAssignment> assignments = new ConcurrentHashMap<>();

    @Override
    public void saveAll(String roomCode, int roundNumber, List<WordAssignment> newAssignments) {
        latestRoundByRoom.put(roomCode, roundNumber);
        newAssignments.forEach(assignment -> assignments.put(key(roomCode, roundNumber, assignment.playerId()), assignment));
    }

    @Override
    public Optional<WordAssignment> findLatestByRoomCodeAndPlayerId(String roomCode, String playerId) {
        Integer roundNumber = latestRoundByRoom.get(roomCode);
        if (roundNumber == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(assignments.get(key(roomCode, roundNumber, playerId)));
    }

    @Override
    public Optional<Integer> findLatestRoundNumber(String roomCode) {
        return Optional.ofNullable(latestRoundByRoom.get(roomCode));
    }

    @Override
    public void deleteByRoomCode(String roomCode) {
        latestRoundByRoom.remove(roomCode);
        assignments.keySet().removeIf(key -> key.startsWith(roomCode + ":"));
    }

    private String key(String roomCode, int roundNumber, String playerId) {
        return roomCode + ":" + roundNumber + ":" + playerId;
    }
}
