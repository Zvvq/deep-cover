package com.cqie.deepcover.word.interfaces.impl;

import com.cqie.deepcover.word.interfaces.WordDescriptionRepository;
import com.cqie.deepcover.word.record.WordDescription;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存描述记录仓库。
 *
 * <p>key 使用 roomCode/round/playerId，保证同一轮同一玩家只有一条描述。</p>
 */
@Repository
@ConditionalOnProperty(name = "deep-cover.word-description.repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryWordDescriptionRepository implements WordDescriptionRepository {
    private final Map<String, WordDescription> descriptions = new ConcurrentHashMap<>();

    @Override
    public void save(WordDescription description) {
        descriptions.put(key(description.roomCode(), description.roundNumber(), description.playerId()), description);
    }

    @Override
    public List<WordDescription> findByRoomCodeAndRound(String roomCode, int roundNumber) {
        String prefix = roomCode + ":" + roundNumber + ":";
        return descriptions.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(WordDescription::number))
                .toList();
    }

    @Override
    public Optional<WordDescription> findByRoomCodeAndRoundAndPlayerId(
            String roomCode,
            int roundNumber,
            String playerId
    ) {
        return Optional.ofNullable(descriptions.get(key(roomCode, roundNumber, playerId)));
    }

    @Override
    public void deleteByRoomCode(String roomCode) {
        descriptions.keySet().removeIf(key -> key.startsWith(roomCode + ":"));
    }

    private String key(String roomCode, int roundNumber, String playerId) {
        return roomCode + ":" + roundNumber + ":" + playerId;
    }
}
