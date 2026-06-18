package com.cqie.deepcover.redis;

import com.cqie.deepcover.chat.record.ChatMessage;
import com.cqie.deepcover.game.enums.GamePhase;
import com.cqie.deepcover.game.interfaces.impl.RedisGameTimerRepository;
import com.cqie.deepcover.game.record.GameTimer;
import com.cqie.deepcover.redis.support.RedisJsonHelper;
import com.cqie.deepcover.redis.support.RedisRoomTtlService;
import com.cqie.deepcover.room.enums.GameMode;
import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.room.interfaces.impl.RedisRoomRepository;
import com.cqie.deepcover.room.model.Room;
import com.cqie.deepcover.room.record.Player;
import com.cqie.deepcover.chat.interfaces.impl.RedisChatMessageRepository;
import com.cqie.deepcover.vote.interfaces.impl.RedisVoteRepository;
import com.cqie.deepcover.vote.record.Vote;
import com.cqie.deepcover.vote.record.VoteSession;
import com.cqie.deepcover.word.interfaces.impl.RedisWordAssignmentRepository;
import com.cqie.deepcover.word.interfaces.impl.RedisWordDescriptionRepository;
import com.cqie.deepcover.word.record.WordAssignment;
import com.cqie.deepcover.word.record.WordDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisRepositoryIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisRoomTtlService ttlService;
    private RedisJsonHelper jsonHelper;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        Clock clock = Clock.fixed(Instant.parse("2026-06-18T03:00:00Z"), ZoneId.of("UTC"));
        ttlService = new RedisRoomTtlService(redisTemplate, Duration.ofHours(2), Duration.ofMinutes(10), clock);
        jsonHelper = new RedisJsonHelper(new ObjectMapper().findAndRegisterModules());
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void redisRepositoriesPersistRoomScopedRuntimeStateAndDeleteItWithRoom() {
        RedisRoomRepository roomRepository = new RedisRoomRepository(redisTemplate, jsonHelper, ttlService);
        RedisGameTimerRepository timerRepository = new RedisGameTimerRepository(redisTemplate, jsonHelper, ttlService);
        RedisChatMessageRepository chatRepository = new RedisChatMessageRepository(redisTemplate, jsonHelper, ttlService);
        RedisVoteRepository voteRepository = new RedisVoteRepository(redisTemplate, jsonHelper, ttlService);
        RedisWordAssignmentRepository assignmentRepository =
                new RedisWordAssignmentRepository(redisTemplate, jsonHelper, ttlService);
        RedisWordDescriptionRepository descriptionRepository =
                new RedisWordDescriptionRepository(redisTemplate, jsonHelper, ttlService);

        Player host = Player.humanHost("player-1", "token-1");
        Room room = Room.createWaiting("ABC123", host, GameMode.WORD_UNDERCOVER);
        room.addPlayer(Player.human("player-2", "token-2"));
        room.markDescribing();
        roomRepository.save(room);

        GameTimer timer = GameTimer.start(
                "ABC123",
                GamePhase.VOTING,
                Duration.ofSeconds(60),
                Instant.parse("2026-06-18T03:01:00Z")
        );
        timerRepository.save(timer);

        ChatMessage message = new ChatMessage(
                "message-1",
                "ABC123",
                "player-1",
                "hello",
                Instant.parse("2026-06-18T03:02:00Z")
        );
        chatRepository.save(message);

        VoteSession session = new VoteSession("ABC123", 1, Instant.parse("2026-06-18T03:03:00Z"), false);
        voteRepository.saveSession(session);
        Vote vote = new Vote("ABC123", 1, "player-1", "player-2", Instant.parse("2026-06-18T03:04:00Z"));
        voteRepository.save(vote);

        WordAssignment assignment = new WordAssignment("ABC123", 1, "player-1", "milk");
        assignmentRepository.saveAll("ABC123", 1, List.of(assignment));
        WordDescription description = new WordDescription(
                "ABC123",
                1,
                "player-1",
                1,
                "RED",
                PlayerType.HUMAN,
                "common drink",
                Instant.parse("2026-06-18T03:05:00Z")
        );
        descriptionRepository.save(description);

        Room restoredRoom = roomRepository.findByCode("ABC123").orElseThrow();
        assertThat(restoredRoom.status()).isEqualTo(RoomStatus.DESCRIBING);
        assertThat(restoredRoom.players()).hasSize(2);
        assertThat(timerRepository.findByRoomCode("ABC123")).contains(timer);
        assertThat(timerRepository.findRunningTimers()).containsExactly(timer);
        assertThat(chatRepository.findByRoomCode("ABC123")).containsExactly(message);
        assertThat(voteRepository.findActiveSession("ABC123")).contains(session);
        assertThat(voteRepository.findLatestSession("ABC123")).contains(session);
        assertThat(voteRepository.findByRoomCodeAndRound("ABC123", 1)).containsExactly(vote);
        assertThat(assignmentRepository.findLatestRoundNumber("ABC123")).contains(1);
        assertThat(assignmentRepository.findLatestByRoomCodeAndPlayerId("ABC123", "player-1")).contains(assignment);
        assertThat(descriptionRepository.findByRoomCodeAndRound("ABC123", 1)).containsExactly(description);
        assertThat(descriptionRepository.findByRoomCodeAndRoundAndPlayerId("ABC123", 1, "player-1"))
                .contains(description);
        assertThat(redisTemplate.getExpire(ttlService.roomKey("ABC123"))).isPositive();

        roomRepository.deleteByCode("ABC123");

        assertThat(roomRepository.findByCode("ABC123")).isEmpty();
        assertThat(timerRepository.findByRoomCode("ABC123")).isEmpty();
        assertThat(chatRepository.findByRoomCode("ABC123")).isEmpty();
        assertThat(voteRepository.findByRoomCode("ABC123")).isEmpty();
        assertThat(assignmentRepository.findLatestRoundNumber("ABC123")).isEmpty();
        assertThat(descriptionRepository.findByRoomCodeAndRound("ABC123", 1)).isEmpty();
    }
}
