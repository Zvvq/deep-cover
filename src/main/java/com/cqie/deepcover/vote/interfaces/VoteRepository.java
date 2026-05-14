package com.cqie.deepcover.vote.interfaces;

import com.cqie.deepcover.vote.record.Vote;
import com.cqie.deepcover.vote.record.VoteSession;

import java.util.List;
import java.util.Optional;

/**
 * 投票仓库接口。当前用内存实现，后续可以替换为数据库或 Redis。
 */
public interface VoteRepository {
    Vote save(Vote vote);

    VoteSession saveSession(VoteSession session);

    Optional<Vote> findByRoomCodeAndRoundAndVoterPlayerId(String roomCode, int roundNumber, String voterPlayerId);

    List<Vote> findByRoomCodeAndRound(String roomCode, int roundNumber);

    List<Vote> findByRoomCode(String roomCode);

    Optional<VoteSession> findActiveSession(String roomCode);

    Optional<VoteSession> findLatestSession(String roomCode);
}
