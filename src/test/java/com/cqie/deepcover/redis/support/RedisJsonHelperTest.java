package com.cqie.deepcover.redis.support;

import com.cqie.deepcover.chat.record.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RedisJsonHelperTest {

    @Test
    void serializesAndDeserializesRecordsWithJavaTimeFields() {
        RedisJsonHelper helper = new RedisJsonHelper(new ObjectMapper().findAndRegisterModules());
        ChatMessage message = new ChatMessage(
                "message-1",
                "ABC123",
                "player-1",
                "hello",
                Instant.parse("2026-06-18T03:00:00Z")
        );

        String json = helper.write(message);
        ChatMessage restored = helper.read(json, ChatMessage.class, "message-1");

        assertThat(restored).isEqualTo(message);
    }
}
