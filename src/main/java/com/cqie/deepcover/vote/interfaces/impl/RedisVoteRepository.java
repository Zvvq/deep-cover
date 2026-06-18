package com.cqie.deepcover.vote.interfaces.impl;

import com.cqie.deepcover.redis.support.RedisJsonHelper;
import com.cqie.deepcover.redis.support.RedisRoomTtlService;
import com.cqie.deepcover.vote.interfaces.VoteRepository;
import com.cqie.deepcover.vote.record.Vote;
import com.cqie.deepcover.vote.record.VoteSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "deep-cover.vote.repository", havingValue = "redis")
public class RedisVoteRepository implements VoteRepository {
    private static final String VOTE_KEY_PREFIX = "dc:vote:";
    private static final String LATEST_ROUND_SUFFIX = ":latest-round";
    private static final String ROUNDS_SUFFIX = ":rounds";

    private final StringRedisTemplate redisTemplate;
    private final RedisJsonHelper jsonHelper;
    private final RedisRoomTtlService ttlService;

    public RedisVoteRepository(
            StringRedisTemplate redisTemplate,
            RedisJsonHelper jsonHelper,
            RedisRoomTtlService ttlService
    ) {
        this.redisTemplate = redisTemplate;
        this.jsonHelper = jsonHelper;
        this.ttlService = ttlService;
    }

    @Override
    public Vote save(Vote vote) {
        String votesKey = votesKey(vote.roomCode(), vote.roundNumber());
        redisTemplate.opsForHash().put(votesKey, vote.voterPlayerId(), jsonHelper.write(vote));
        redisTemplate.opsForSet().add(roundsKey(vote.roomCode()), String.valueOf(vote.roundNumber()));
        ttlService.registerActiveKey(vote.roomCode(), votesKey);
        ttlService.registerActiveKey(vote.roomCode(), roundsKey(vote.roomCode()));
        return vote;
    }

    @Override
    public VoteSession saveSession(VoteSession session) {
        String sessionKey = sessionKey(session.roomCode(), session.roundNumber());
        redisTemplate.opsForValue().set(sessionKey, jsonHelper.write(session));
        redisTemplate.opsForValue().set(latestRoundKey(session.roomCode()), String.valueOf(session.roundNumber()));
        redisTemplate.opsForSet().add(roundsKey(session.roomCode()), String.valueOf(session.roundNumber()));
        ttlService.registerActiveKey(session.roomCode(), sessionKey);
        ttlService.registerActiveKey(session.roomCode(), latestRoundKey(session.roomCode()));
        ttlService.registerActiveKey(session.roomCode(), roundsKey(session.roomCode()));
        return session;
    }

    @Override
    public Optional<Vote> findByRoomCodeAndRoundAndVoterPlayerId(
            String roomCode,
            int roundNumber,
            String voterPlayerId
    ) {
        Object value = redisTemplate.opsForHash().get(votesKey(roomCode, roundNumber), voterPlayerId);
        if (value == null) {
            return Optional.empty();
        }
        ttlService.touchRoom(roomCode);
        return Optional.of(jsonHelper.read(value.toString(), Vote.class, voterPlayerId));
    }

    @Override
    public List<Vote> findByRoomCodeAndRound(String roomCode, int roundNumber) {
        String key = votesKey(roomCode, roundNumber);
        List<Vote> votes = redisTemplate.opsForHash().values(key).stream()
                .map(value -> jsonHelper.read(value.toString(), Vote.class, key))
                .sorted(Comparator.comparing(Vote::createdAt))
                .toList();
        if (!votes.isEmpty()) {
            ttlService.touchRoom(roomCode);
        }
        return votes;
    }

    @Override
    public List<Vote> findByRoomCode(String roomCode) {
        var rounds = redisTemplate.opsForSet().members(roundsKey(roomCode));
        if (rounds == null || rounds.isEmpty()) {
            return List.of();
        }
        return rounds.stream()
                .map(Integer::parseInt)
                .sorted()
                .flatMap(round -> findByRoomCodeAndRound(roomCode, round).stream())
                .sorted(Comparator.comparing(Vote::createdAt))
                .toList();
    }

    @Override
    public Optional<VoteSession> findActiveSession(String roomCode) {
        return findLatestSession(roomCode)
                .filter(session -> !session.settled());
    }

    @Override
    public Optional<VoteSession> findLatestSession(String roomCode) {
        String latestRound = redisTemplate.opsForValue().get(latestRoundKey(roomCode));
        if (latestRound == null) {
            return Optional.empty();
        }
        String key = sessionKey(roomCode, Integer.parseInt(latestRound));
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        ttlService.touchRoom(roomCode);
        return Optional.of(jsonHelper.read(json, VoteSession.class, key));
    }

    private String latestRoundKey(String roomCode) {
        return VOTE_KEY_PREFIX + roomCode + LATEST_ROUND_SUFFIX;
    }

    private String roundsKey(String roomCode) {
        return VOTE_KEY_PREFIX + roomCode + ROUNDS_SUFFIX;
    }

    private String sessionKey(String roomCode, int roundNumber) {
        return VOTE_KEY_PREFIX + roomCode + ":session:" + roundNumber;
    }

    private String votesKey(String roomCode, int roundNumber) {
        return VOTE_KEY_PREFIX + roomCode + ":round:" + roundNumber + ":votes";
    }
}
