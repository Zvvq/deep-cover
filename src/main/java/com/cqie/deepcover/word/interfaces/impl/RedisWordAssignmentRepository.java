package com.cqie.deepcover.word.interfaces.impl;

import com.cqie.deepcover.redis.support.RedisJsonHelper;
import com.cqie.deepcover.redis.support.RedisRoomTtlService;
import com.cqie.deepcover.word.interfaces.WordAssignmentRepository;
import com.cqie.deepcover.word.record.WordAssignment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "deep-cover.word-assignment.repository", havingValue = "redis")
public class RedisWordAssignmentRepository implements WordAssignmentRepository {
    private static final String WORD_KEY_PREFIX = "dc:word:";
    private static final String LATEST_ROUND_SUFFIX = ":latest-round";

    private final StringRedisTemplate redisTemplate;
    private final RedisJsonHelper jsonHelper;
    private final RedisRoomTtlService ttlService;

    public RedisWordAssignmentRepository(
            StringRedisTemplate redisTemplate,
            RedisJsonHelper jsonHelper,
            RedisRoomTtlService ttlService
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonHelper = jsonHelper;
        this.ttlService = ttlService;
    }

    @Override
    public void saveAll(String roomCode, int roundNumber, List<WordAssignment> assignments) {
        String assignmentsKey = assignmentsKey(roomCode, roundNumber);
        assignments.forEach(assignment ->
                redisTemplate.opsForHash().put(assignmentsKey, assignment.playerId(), jsonHelper.write(assignment))
        );
        redisTemplate.opsForValue().set(latestRoundKey(roomCode), String.valueOf(roundNumber));
        ttlService.registerActiveKey(roomCode, assignmentsKey);
        ttlService.registerActiveKey(roomCode, latestRoundKey(roomCode));
    }

    @Override
    public Optional<WordAssignment> findLatestByRoomCodeAndPlayerId(String roomCode, String playerId) {
        Optional<Integer> roundNumber = findLatestRoundNumber(roomCode);
        if (roundNumber.isEmpty()) {
            return Optional.empty();
        }
        String key = assignmentsKey(roomCode, roundNumber.get());
        Object value = redisTemplate.opsForHash().get(key, playerId);
        if (value == null) {
            return Optional.empty();
        }
        ttlService.touchRoom(roomCode);
        return Optional.of(jsonHelper.read(value.toString(), WordAssignment.class, key));
    }

    @Override
    public Optional<Integer> findLatestRoundNumber(String roomCode) {
        String roundNumber = redisTemplate.opsForValue().get(latestRoundKey(roomCode));
        if (roundNumber == null) {
            return Optional.empty();
        }
        ttlService.touchRoom(roomCode);
        return Optional.of(Integer.parseInt(roundNumber));
    }

    @Override
    public void deleteByRoomCode(String roomCode) {
        var keys = redisTemplate.keys(WORD_KEY_PREFIX + roomCode + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String latestRoundKey(String roomCode) {
        return WORD_KEY_PREFIX + roomCode + LATEST_ROUND_SUFFIX;
    }

    private String assignmentsKey(String roomCode, int roundNumber) {
        return WORD_KEY_PREFIX + roomCode + ":round:" + roundNumber + ":assignments";
    }
}
