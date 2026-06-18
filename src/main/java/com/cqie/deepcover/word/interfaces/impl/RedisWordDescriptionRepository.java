package com.cqie.deepcover.word.interfaces.impl;

import com.cqie.deepcover.redis.support.RedisJsonHelper;
import com.cqie.deepcover.redis.support.RedisRoomTtlService;
import com.cqie.deepcover.word.interfaces.WordDescriptionRepository;
import com.cqie.deepcover.word.record.WordDescription;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "deep-cover.word-description.repository", havingValue = "redis")
public class RedisWordDescriptionRepository implements WordDescriptionRepository {
    private static final String DESCRIPTION_KEY_PREFIX = "dc:word-description:";

    private final StringRedisTemplate redisTemplate;
    private final RedisJsonHelper jsonHelper;
    private final RedisRoomTtlService ttlService;

    public RedisWordDescriptionRepository(
            StringRedisTemplate redisTemplate,
            RedisJsonHelper jsonHelper,
            RedisRoomTtlService ttlService
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonHelper = jsonHelper;
        this.ttlService = ttlService;
    }

    @Override
    public void save(WordDescription description) {
        String key = descriptionsKey(description.roomCode(), description.roundNumber());
        redisTemplate.opsForHash().put(key, description.playerId(), jsonHelper.write(description));
        ttlService.registerActiveKey(description.roomCode(), key);
    }

    @Override
    public List<WordDescription> findByRoomCodeAndRound(String roomCode, int roundNumber) {
        String key = descriptionsKey(roomCode, roundNumber);
        List<WordDescription> descriptions = redisTemplate.opsForHash().values(key).stream()
                .map(value -> jsonHelper.read(value.toString(), WordDescription.class, key))
                .sorted(Comparator.comparing(WordDescription::number))
                .toList();
        if (!descriptions.isEmpty()) {
            ttlService.touchRoom(roomCode);
        }
        return descriptions;
    }

    @Override
    public Optional<WordDescription> findByRoomCodeAndRoundAndPlayerId(
            String roomCode,
            int roundNumber,
            String playerId
    ) {
        String key = descriptionsKey(roomCode, roundNumber);
        Object value = redisTemplate.opsForHash().get(key, playerId);
        if (value == null) {
            return Optional.empty();
        }
        ttlService.touchRoom(roomCode);
        return Optional.of(jsonHelper.read(value.toString(), WordDescription.class, key));
    }

    @Override
    public void deleteByRoomCode(String roomCode) {
        var keys = redisTemplate.keys(DESCRIPTION_KEY_PREFIX + roomCode + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String descriptionsKey(String roomCode, int roundNumber) {
        return DESCRIPTION_KEY_PREFIX + roomCode + ":round:" + roundNumber + ":descriptions";
    }
}
