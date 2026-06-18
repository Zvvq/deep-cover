package com.cqie.deepcover.chat.interfaces.impl;

import com.cqie.deepcover.chat.interfaces.ChatMessageRepository;
import com.cqie.deepcover.chat.record.ChatMessage;
import com.cqie.deepcover.redis.support.RedisJsonHelper;
import com.cqie.deepcover.redis.support.RedisRoomTtlService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnProperty(name = "deep-cover.chat.repository", havingValue = "redis")
public class RedisChatMessageRepository implements ChatMessageRepository {
    private static final String CHAT_KEY_PREFIX = "dc:chat:";
    private static final String CHAT_KEY_SUFFIX = ":messages";

    private final StringRedisTemplate redisTemplate;
    private final RedisJsonHelper jsonHelper;
    private final RedisRoomTtlService ttlService;

    public RedisChatMessageRepository(
            StringRedisTemplate redisTemplate,
            RedisJsonHelper jsonHelper,
            RedisRoomTtlService ttlService
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonHelper = jsonHelper;
        this.ttlService = ttlService;
    }

    @Override
    public ChatMessage save(ChatMessage message) {
        String key = chatKey(message.roomCode());
        redisTemplate.opsForList().rightPush(key, jsonHelper.write(message));
        ttlService.registerActiveKey(message.roomCode(), key);
        return message;
    }

    @Override
    public List<ChatMessage> findByRoomCode(String roomCode) {
        String key = chatKey(roomCode);
        List<String> values = redisTemplate.opsForList().range(key, 0, -1);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        ttlService.touchRoom(roomCode);
        return values.stream()
                .map(value -> jsonHelper.read(value, ChatMessage.class, key))
                .toList();
    }

    private String chatKey(String roomCode) {
        return CHAT_KEY_PREFIX + roomCode + CHAT_KEY_SUFFIX;
    }
}
