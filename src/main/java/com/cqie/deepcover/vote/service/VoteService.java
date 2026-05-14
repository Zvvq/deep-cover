package com.cqie.deepcover.vote.service;

import com.cqie.deepcover.chat.enums.RoomEventType;
import com.cqie.deepcover.chat.interfaces.ChatEventPublisher;
import com.cqie.deepcover.chat.record.RoomEvent;
import com.cqie.deepcover.game.enums.GamePhase;
import com.cqie.deepcover.game.service.GameTimerService;
import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.room.enums.RoomErrorCode;
import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.room.exception.RoomException;
import com.cqie.deepcover.room.record.Player;
import com.cqie.deepcover.room.record.PlayerSnapshot;
import com.cqie.deepcover.room.record.RoomSnapshot;
import com.cqie.deepcover.room.service.RoomService;
import com.cqie.deepcover.vote.enums.GameWinner;
import com.cqie.deepcover.vote.interfaces.VoteRepository;
import com.cqie.deepcover.vote.record.GameEndedPayload;
import com.cqie.deepcover.vote.record.PlayerEliminatedPayload;
import com.cqie.deepcover.vote.record.RoundStartedPayload;
import com.cqie.deepcover.vote.record.Vote;
import com.cqie.deepcover.vote.record.VoteRequest;
import com.cqie.deepcover.vote.record.VoteResult;
import com.cqie.deepcover.vote.record.VoteSession;
import com.cqie.deepcover.vote.record.VoteSnapshot;
import com.cqie.deepcover.vote.record.VoteUpdatedPayload;
import com.cqie.deepcover.vote.record.VotingStartedEvent;
import com.cqie.deepcover.vote.record.VotingStartedPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 投票模块的核心业务服务。
 *
 * <p>它负责投票阶段开始、提交投票、重复投票校验、淘汰结算和胜负判断。
 * AI 投票最终也通过这里落库，避免 AI 绕过游戏规则。</p>
 */
@Service
public class VoteService {
    private static final Duration VOTING_DURATION = Duration.ofSeconds(60);
    private static final Duration CHATTING_DURATION = Duration.ofSeconds(300);

    private final RoomService roomService;
    private final VoteRepository voteRepository;
    private final ChatEventPublisher chatEventPublisher;
    private final GameTimerService gameTimerService;
    private final Clock clock;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Random random = new SecureRandom();

    public VoteService(
            RoomService roomService,
            VoteRepository voteRepository,
            ChatEventPublisher chatEventPublisher,
            GameTimerService gameTimerService,
            Clock clock
    ) {
        this(roomService, voteRepository, chatEventPublisher, gameTimerService, clock, event -> {
        });
    }

    @Autowired
    public VoteService(
            RoomService roomService,
            VoteRepository voteRepository,
            ChatEventPublisher chatEventPublisher,
            GameTimerService gameTimerService,
            Clock clock,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.roomService = roomService;
        this.voteRepository = voteRepository;
        this.chatEventPublisher = chatEventPublisher;
        this.gameTimerService = gameTimerService;
        this.clock = clock;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 聊天计时结束后进入投票阶段。
     */
    public synchronized VoteSnapshot startVoting(String roomCode) {
        RoomSnapshot room = roomService.snapshot(roomCode);
        if (room.status() == RoomStatus.VOTING) {
            return systemSnapshot(roomCode);
        }
        if (room.status() != RoomStatus.CHATTING) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_CHATTING, "Room is not chatting.");
        }

        int roundNumber = nextRoundNumber(roomCode);
        room = roomService.markVoting(roomCode);
        VoteSession session = voteRepository.saveSession(
                new VoteSession(roomCode, roundNumber, clock.instant(), false)
        );
        gameTimerService.startTimer(roomCode, GamePhase.VOTING, VOTING_DURATION);

        chatEventPublisher.publish(
                roomCode,
                new RoomEvent(RoomEventType.VOTING_STARTED, new VotingStartedPayload(
                        roomCode,
                        roundNumber,
                        alivePlayers(room).stream().map(PlayerSnapshot::id).toList()
                ))
        );
        applicationEventPublisher.publishEvent(new VotingStartedEvent(roomCode, session.roundNumber()));
        return snapshotFromSession(room, session, null);
    }

    /**
     * 真人玩家提交投票。
     */
    public synchronized VoteResult castVote(String roomCode, String playerToken, VoteRequest request) {
        Player voter = roomService.requireVotingParticipant(roomCode, playerToken);
        return castVoteByPlayer(roomCode, voter.id(), request == null ? null : request.targetPlayerId());
    }

    /**
     * 系统内部提交 AI 投票。
     */
    public synchronized VoteResult castSystemVote(String roomCode, String voterPlayerId, String targetPlayerId) {
        Player voter = roomService.requireAliveAiVotingParticipant(roomCode, voterPlayerId);
        return castVoteByPlayer(roomCode, voter.id(), targetPlayerId);
    }

    public synchronized VoteSnapshot snapshot(String roomCode, String playerToken) {
        Player player = roomService.requireVotingParticipant(roomCode, playerToken);
        VoteSession session = activeSession(roomCode);
        RoomSnapshot room = roomService.snapshot(roomCode);
        return snapshotFromSession(room, session, player.id());
    }

    public synchronized int currentRound(String roomCode) {
        return voteRepository.findActiveSession(roomCode)
                .or(() -> voteRepository.findLatestSession(roomCode))
                .map(VoteSession::roundNumber)
                .orElse(0);
    }

    public synchronized boolean hasVoted(String roomCode, int roundNumber, String voterPlayerId) {
        return voteRepository.findByRoomCodeAndRoundAndVoterPlayerId(roomCode, roundNumber, voterPlayerId)
                .isPresent();
    }

    /**
     * 投票计时结束，或所有存活玩家都投完时，调用这里结算。
     */
    public synchronized VoteResult settleVoting(String roomCode) {
        RoomSnapshot room = roomService.snapshot(roomCode);
        if (room.status() != RoomStatus.VOTING) {
            return VoteResult.notSettled(roomCode, currentRound(roomCode));
        }

        VoteSession session = activeSession(roomCode);
        List<Vote> votes = voteRepository.findByRoomCodeAndRound(roomCode, session.roundNumber());
        String eliminatedPlayerId = chooseEliminatedPlayer(room, votes);
        PlayerSnapshot eliminatedPlayer = findPlayer(room, eliminatedPlayerId);

        RoomSnapshot afterElimination = roomService.eliminatePlayer(roomCode, eliminatedPlayerId);
        voteRepository.saveSession(session.settle());
        publishPlayerEliminated(roomCode, session.roundNumber(), eliminatedPlayer);

        GameWinner winner = determineWinner(eliminatedPlayer, afterElimination);
        if (winner != null) {
            roomService.markEnded(roomCode);
            publishGameEnded(roomCode, winner, eliminatedPlayer);
        } else {
            roomService.markChatting(roomCode);
            gameTimerService.startTimer(roomCode, GamePhase.CHATTING, CHATTING_DURATION);
            chatEventPublisher.publish(
                    roomCode,
                    new RoomEvent(RoomEventType.ROUND_STARTED, new RoundStartedPayload(roomCode, session.roundNumber() + 1))
            );
        }

        return new VoteResult(
                roomCode,
                session.roundNumber(),
                true,
                eliminatedPlayerId,
                eliminatedPlayer.type(),
                winner
        );
    }

    private VoteResult castVoteByPlayer(String roomCode, String voterPlayerId, String targetPlayerId) {
        RoomSnapshot room = roomService.snapshot(roomCode);
        if (room.status() != RoomStatus.VOTING) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_VOTING, "Room is not voting.");
        }
        VoteSession session = activeSession(roomCode);

        if (voteRepository.findByRoomCodeAndRoundAndVoterPlayerId(roomCode, session.roundNumber(), voterPlayerId)
                .isPresent()) {
            throw new RoomException(RoomErrorCode.DUPLICATE_VOTE, "Player already voted.");
        }

        validateVoteTarget(room, voterPlayerId, targetPlayerId);
        voteRepository.save(new Vote(roomCode, session.roundNumber(), voterPlayerId, targetPlayerId, clock.instant()));
        publishVoteUpdated(roomCode, session.roundNumber());

        if (voteRepository.findByRoomCodeAndRound(roomCode, session.roundNumber()).size() >= requiredVoteCount(room)) {
            return settleVoting(roomCode);
        }
        return VoteResult.notSettled(roomCode, session.roundNumber());
    }

    private void validateVoteTarget(RoomSnapshot room, String voterPlayerId, String targetPlayerId) {
        if (targetPlayerId == null || targetPlayerId.isBlank()) {
            throw new RoomException(RoomErrorCode.INVALID_VOTE, "Vote target cannot be empty.");
        }
        if (voterPlayerId.equals(targetPlayerId)) {
            throw new RoomException(RoomErrorCode.INVALID_VOTE, "Player cannot vote for self.");
        }
        PlayerSnapshot target = findPlayer(room, targetPlayerId);
        if (!target.alive()) {
            throw new RoomException(RoomErrorCode.INVALID_VOTE, "Vote target is not alive.");
        }
    }

    private VoteSession activeSession(String roomCode) {
        return voteRepository.findActiveSession(roomCode)
                .orElseThrow(() -> new RoomException(RoomErrorCode.INVALID_VOTE, "Voting session not found."));
    }

    private int nextRoundNumber(String roomCode) {
        return voteRepository.findLatestSession(roomCode)
                .map(session -> session.roundNumber() + 1)
                .orElse(1);
    }

    private VoteSnapshot systemSnapshot(String roomCode) {
        VoteSession session = activeSession(roomCode);
        return snapshotFromSession(roomService.snapshot(roomCode), session, null);
    }

    private VoteSnapshot snapshotFromSession(RoomSnapshot room, VoteSession session, String currentPlayerId) {
        List<Vote> votes = voteRepository.findByRoomCodeAndRound(room.roomCode(), session.roundNumber());
        boolean currentPlayerVoted = currentPlayerId != null && votes.stream()
                .anyMatch(vote -> vote.voterPlayerId().equals(currentPlayerId));
        return new VoteSnapshot(
                room.roomCode(),
                session.roundNumber(),
                requiredVoteCount(room),
                votes.size(),
                currentPlayerVoted
        );
    }

    private int requiredVoteCount(RoomSnapshot room) {
        return alivePlayers(room).size();
    }

    private List<PlayerSnapshot> alivePlayers(RoomSnapshot room) {
        return room.players().stream()
                .filter(PlayerSnapshot::alive)
                .toList();
    }

    private PlayerSnapshot findPlayer(RoomSnapshot room, String playerId) {
        return room.players().stream()
                .filter(player -> player.id().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new RoomException(RoomErrorCode.PLAYER_NOT_FOUND, "Player not found."));
    }

    private String chooseEliminatedPlayer(RoomSnapshot room, List<Vote> votes) {
        if (votes.isEmpty()) {
            return randomAlivePlayer(room).id();
        }

        Map<String, Long> voteCounts = votes.stream()
                .map(Vote::targetPlayerId)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        long maxVoteCount = voteCounts.values().stream()
                .max(Comparator.naturalOrder())
                .orElse(0L);
        List<String> topTargets = voteCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == maxVoteCount)
                .map(Map.Entry::getKey)
                .toList();
        return topTargets.get(random.nextInt(topTargets.size()));
    }

    private PlayerSnapshot randomAlivePlayer(RoomSnapshot room) {
        List<PlayerSnapshot> candidates = new ArrayList<>(alivePlayers(room));
        if (candidates.isEmpty()) {
            throw new RoomException(RoomErrorCode.INVALID_VOTE, "No alive player can be eliminated.");
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private GameWinner determineWinner(PlayerSnapshot eliminatedPlayer, RoomSnapshot afterElimination) {
        if (eliminatedPlayer.type() == PlayerType.AI) {
            return GameWinner.HUMAN;
        }

        long aliveHuman = afterElimination.players().stream()
                .filter(player -> player.alive() && player.type() == PlayerType.HUMAN)
                .count();
        long aliveAi = afterElimination.players().stream()
                .filter(player -> player.alive() && player.type() == PlayerType.AI)
                .count();
        if (aliveAi >= aliveHuman) {
            return GameWinner.AI;
        }
        return null;
    }

    private void publishVoteUpdated(String roomCode, int roundNumber) {
        RoomSnapshot room = roomService.snapshot(roomCode);
        chatEventPublisher.publish(
                roomCode,
                new RoomEvent(RoomEventType.VOTE_UPDATED, new VoteUpdatedPayload(
                        roomCode,
                        roundNumber,
                        voteRepository.findByRoomCodeAndRound(roomCode, roundNumber).size(),
                        requiredVoteCount(room)
                ))
        );
    }

    private void publishPlayerEliminated(String roomCode, int roundNumber, PlayerSnapshot eliminatedPlayer) {
        chatEventPublisher.publish(
                roomCode,
                new RoomEvent(RoomEventType.PLAYER_ELIMINATED, new PlayerEliminatedPayload(
                        roomCode,
                        roundNumber,
                        eliminatedPlayer.id(),
                        eliminatedPlayer.type()
                ))
        );
    }

    private void publishGameEnded(String roomCode, GameWinner winner, PlayerSnapshot eliminatedPlayer) {
        String reason = eliminatedPlayer.type() == PlayerType.AI
                ? "AI player was eliminated."
                : "Alive AI count reached alive human count.";
        chatEventPublisher.publish(
                roomCode,
                new RoomEvent(RoomEventType.GAME_ENDED, new GameEndedPayload(roomCode, winner, reason))
        );
    }
}
