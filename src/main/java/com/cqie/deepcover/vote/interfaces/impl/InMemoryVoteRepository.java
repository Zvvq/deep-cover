package com.cqie.deepcover.vote.interfaces.impl;

import com.cqie.deepcover.vote.interfaces.VoteRepository;
import com.cqie.deepcover.vote.record.Vote;
import com.cqie.deepcover.vote.record.VoteSession;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 投票模块的内存仓库实现。
 */
@Repository
public class InMemoryVoteRepository implements VoteRepository {
    private final Map<String, Vote> votes = new ConcurrentHashMap<>();
    private final Map<String, VoteSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Vote save(Vote vote) {
        votes.put(voteKey(vote.roomCode(), vote.roundNumber(), vote.voterPlayerId()), vote);
        return vote;
    }

    @Override
    public VoteSession saveSession(VoteSession session) {
        sessions.put(sessionKey(session.roomCode(), session.roundNumber()), session);
        return session;
    }

    @Override
    public Optional<Vote> findByRoomCodeAndRoundAndVoterPlayerId(
            String roomCode,
            int roundNumber,
            String voterPlayerId
    ) {
        return Optional.ofNullable(votes.get(voteKey(roomCode, roundNumber, voterPlayerId)));
    }

    @Override
    public List<Vote> findByRoomCodeAndRound(String roomCode, int roundNumber) {
        return votes.values().stream()
                .filter(vote -> vote.roomCode().equals(roomCode))
                .filter(vote -> vote.roundNumber() == roundNumber)
                .sorted(Comparator.comparing(Vote::createdAt))
                .toList();
    }

    @Override
    public List<Vote> findByRoomCode(String roomCode) {
        return votes.values().stream()
                .filter(vote -> vote.roomCode().equals(roomCode))
                .sorted(Comparator.comparing(Vote::createdAt))
                .toList();
    }

    @Override
    public Optional<VoteSession> findActiveSession(String roomCode) {
        return sessions.values().stream()
                .filter(session -> session.roomCode().equals(roomCode))
                .filter(session -> !session.settled())
                .max(Comparator.comparingInt(VoteSession::roundNumber));
    }

    @Override
    public Optional<VoteSession> findLatestSession(String roomCode) {
        return sessions.values().stream()
                .filter(session -> session.roomCode().equals(roomCode))
                .max(Comparator.comparingInt(VoteSession::roundNumber));
    }

    private String voteKey(String roomCode, int roundNumber, String voterPlayerId) {
        return roomCode + ":" + roundNumber + ":" + voterPlayerId;
    }

    private String sessionKey(String roomCode, int roundNumber) {
        return roomCode + ":" + roundNumber;
    }
}
