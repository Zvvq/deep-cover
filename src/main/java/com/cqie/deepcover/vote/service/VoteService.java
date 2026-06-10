package com.cqie.deepcover.vote.service;

import com.cqie.deepcover.chat.enums.RoomEventType;
import com.cqie.deepcover.chat.interfaces.ChatEventPublisher;
import com.cqie.deepcover.chat.record.RoomEvent;
import com.cqie.deepcover.game.enums.GamePhase;
import com.cqie.deepcover.game.service.GameTimerService;
import com.cqie.deepcover.room.enums.GameMode;
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
import com.cqie.deepcover.vote.record.GameEndedEvent;
import com.cqie.deepcover.vote.record.PlayerEliminatedPayload;
import com.cqie.deepcover.vote.record.PlayerEliminatedEvent;
import com.cqie.deepcover.vote.record.RoundStartedPayload;
import com.cqie.deepcover.vote.record.RoundStartedEvent;
import com.cqie.deepcover.vote.record.Vote;
import com.cqie.deepcover.vote.record.VoteRequest;
import com.cqie.deepcover.vote.record.VoteResult;
import com.cqie.deepcover.vote.record.VoteSession;
import com.cqie.deepcover.vote.record.VoteSnapshot;
import com.cqie.deepcover.vote.record.VoteUpdatedPayload;
import com.cqie.deepcover.vote.record.VotingStartedEvent;
import com.cqie.deepcover.vote.record.VotingStartedPayload;
import com.cqie.deepcover.word.interfaces.impl.InMemoryWordAssignmentRepository;
import com.cqie.deepcover.word.interfaces.impl.InMemoryWordPairRepository;
import com.cqie.deepcover.word.record.WordRoundStartedEvent;
import com.cqie.deepcover.word.record.WordRoundStartedPayload;
import com.cqie.deepcover.word.service.WordGameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(VoteService.class);
    private static final Duration VOTING_DURATION = Duration.ofSeconds(60);
    private static final Duration CHATTING_DURATION = Duration.ofSeconds(300);

    private final RoomService roomService;
    private final VoteRepository voteRepository;
    private final ChatEventPublisher chatEventPublisher;
    private final GameTimerService gameTimerService;
    private final Clock clock;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WordGameService wordGameService;
    private final Random random = new SecureRandom();

    public VoteService(
            RoomService roomService,
            VoteRepository voteRepository,
            ChatEventPublisher chatEventPublisher,
            GameTimerService gameTimerService,
            Clock clock
    ) {
        this(roomService, voteRepository, chatEventPublisher, gameTimerService, clock, event -> {
        }, defaultWordGameService());
    }

    public VoteService(
            RoomService roomService,
            VoteRepository voteRepository,
            ChatEventPublisher chatEventPublisher,
            GameTimerService gameTimerService,
            Clock clock,
            WordGameService wordGameService
    ) {
        this(roomService, voteRepository, chatEventPublisher, gameTimerService, clock, event -> {
        }, wordGameService);
    }

    public VoteService(
            RoomService roomService,
            VoteRepository voteRepository,
            ChatEventPublisher chatEventPublisher,
            GameTimerService gameTimerService,
            Clock clock,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this(roomService, voteRepository, chatEventPublisher, gameTimerService, clock, applicationEventPublisher,
                defaultWordGameService());
    }

    @Autowired
    public VoteService(
            RoomService roomService,
            VoteRepository voteRepository,
            ChatEventPublisher chatEventPublisher,
            GameTimerService gameTimerService,
            Clock clock,
            ApplicationEventPublisher applicationEventPublisher,
            WordGameService wordGameService
    ) {
        this.roomService = roomService;
        this.voteRepository = voteRepository;
        this.chatEventPublisher = chatEventPublisher;
        this.gameTimerService = gameTimerService;
        this.clock = clock;
        this.applicationEventPublisher = applicationEventPublisher;
        this.wordGameService = wordGameService;
    }

    /**
     * 聊天计时结束后进入投票阶段。
     */
    public synchronized VoteSnapshot startVoting(String roomCode) {
        RoomSnapshot room = roomService.snapshot(roomCode);
        if (room.status() == RoomStatus.VOTING) {
            log.info("房间已处于投票阶段，直接返回当前投票状态，roomCode={}", roomCode);
            return systemSnapshot(roomCode);
        }
        if (!canStartVoting(room)) {
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
        log.info("开始投票，roomCode={}, roundNumber={}, alivePlayerCount={}, requiredVoteCount={}",
                roomCode, session.roundNumber(), alivePlayers(room).size(), requiredVoteCount(room));
        return snapshotFromSession(room, session, null);
    }

    /**
     * 真人玩家提交投票。
     */
    public synchronized VoteResult castVote(String roomCode, String playerToken, VoteRequest request) {
        Player voter = roomService.requireVotingParticipant(roomCode, playerToken);
        return castVoteByPlayer(roomCode, voter.id(), voter.type(), request == null ? null : request.targetPlayerId());
    }

    /**
     * 系统内部提交 AI 投票。
     */
    public synchronized VoteResult castSystemVote(String roomCode, String voterPlayerId, String targetPlayerId) {
        Player voter = roomService.requireAliveAiVotingParticipant(roomCode, voterPlayerId);
        return castVoteByPlayer(roomCode, voter.id(), voter.type(), targetPlayerId);
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
            log.info("结算投票时房间不在投票阶段，跳过结算，roomCode={}, status={}", roomCode, room.status());
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
        } else if (afterElimination.gameMode() == GameMode.WORD_UNDERCOVER) {
            int nextRoundNumber = session.roundNumber() + 1;
            RoomSnapshot nextRoundRoom = roomService.markDescribing(roomCode);
            wordGameService.assignWords(nextRoundRoom, nextRoundNumber);
            PlayerSnapshot currentPlayer = firstAlivePlayerByNumber(nextRoundRoom);
            chatEventPublisher.publish(
                    roomCode,
                    new RoomEvent(RoomEventType.WORD_ROUND_STARTED, new WordRoundStartedPayload(
                            roomCode,
                            nextRoundNumber,
                            currentPlayer == null ? null : currentPlayer.id(),
                            currentPlayer == null ? null : currentPlayer.number()
                    ))
            );
            applicationEventPublisher.publishEvent(new WordRoundStartedEvent(
                    roomCode,
                    nextRoundNumber,
                    currentPlayer == null ? null : currentPlayer.id(),
                    currentPlayer == null ? null : currentPlayer.number()
            ));
            log.info("进入下一轮关键词描述，roomCode={}, nextRoundNumber={}, currentPlayerId={}, currentNumber={}",
                    roomCode,
                    nextRoundNumber,
                    currentPlayer == null ? null : currentPlayer.id(),
                    currentPlayer == null ? null : currentPlayer.number());
        } else {
            RoomSnapshot nextRoundRoom = roomService.markChattingWithNewTopic(roomCode);
            gameTimerService.startTimer(roomCode, GamePhase.CHATTING, CHATTING_DURATION);
            chatEventPublisher.publish(
                    roomCode,
                    new RoomEvent(RoomEventType.ROUND_STARTED, new RoundStartedPayload(
                            roomCode,
                            session.roundNumber() + 1,
                            nextRoundRoom.topic()
                    ))
            );
            applicationEventPublisher.publishEvent(new RoundStartedEvent(
                    roomCode,
                    session.roundNumber() + 1,
                    nextRoundRoom.topic()
            ));
            log.info("进入下一轮聊天，roomCode={}, nextRoundNumber={}, topicId={}, topicContent={}",
                    roomCode, session.roundNumber() + 1, nextRoundRoom.topic().id(), nextRoundRoom.topic().content());
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

    private VoteResult castVoteByPlayer(String roomCode, String voterPlayerId, PlayerType voterType, String targetPlayerId) {
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
        int submittedVoteCount = voteRepository.findByRoomCodeAndRound(roomCode, session.roundNumber()).size();
        log.info("玩家提交投票，roomCode={}, roundNumber={}, voterPlayerId={}, voterType={}, targetPlayerId={}, submittedVoteCount={}, requiredVoteCount={}",
                roomCode, session.roundNumber(), voterPlayerId, voterType, targetPlayerId, submittedVoteCount, requiredVoteCount(room));

        if (submittedVoteCount >= requiredVoteCount(room)) {
            return settleVoting(roomCode);
        }
        return VoteResult.notSettled(roomCode, session.roundNumber());
    }

    private boolean canStartVoting(RoomSnapshot room) {
        return room.status() == RoomStatus.CHATTING
                || (room.gameMode() == GameMode.WORD_UNDERCOVER && room.status() == RoomStatus.DESCRIBING);
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

    /**
     * 系统内部查询投票状态时使用，不绑定某个真人玩家 token。
     */
    public synchronized VoteSnapshot systemSnapshot(String roomCode) {
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
            log.info("投票阶段无人投票，随机淘汰存活玩家，roomCode={}", room.roomCode());
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
        log.info("投票统计完成，roomCode={}, voteCounts={}, topTargets={}", room.roomCode(), voteCounts, topTargets);
        return topTargets.get(random.nextInt(topTargets.size()));
    }

    private PlayerSnapshot randomAlivePlayer(RoomSnapshot room) {
        List<PlayerSnapshot> candidates = new ArrayList<>(alivePlayers(room));
        if (candidates.isEmpty()) {
            throw new RoomException(RoomErrorCode.INVALID_VOTE, "No alive player can be eliminated.");
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private PlayerSnapshot firstAlivePlayerByNumber(RoomSnapshot room) {
        return alivePlayers(room).stream()
                .min(Comparator.comparing(PlayerSnapshot::number))
                .orElse(null);
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
        applicationEventPublisher.publishEvent(new PlayerEliminatedEvent(
                roomCode,
                roundNumber,
                eliminatedPlayer.id(),
                eliminatedPlayer.type()
        ));
        log.info("投票结算淘汰玩家，roomCode={}, roundNumber={}, eliminatedPlayerId={}, playerType={}",
                roomCode, roundNumber, eliminatedPlayer.id(), eliminatedPlayer.type());
    }

    private void publishGameEnded(String roomCode, GameWinner winner, PlayerSnapshot eliminatedPlayer) {
        String reason = eliminatedPlayer.type() == PlayerType.AI
                ? "AI player was eliminated."
                : "Alive AI count reached alive human count.";
        chatEventPublisher.publish(
                roomCode,
                new RoomEvent(RoomEventType.GAME_ENDED, new GameEndedPayload(roomCode, winner, reason))
        );
        applicationEventPublisher.publishEvent(new GameEndedEvent(roomCode, winner, reason));
        log.info("游戏结束，roomCode={}, winner={}, reason={}", roomCode, winner, reason);
    }

    private static WordGameService defaultWordGameService() {
        return new WordGameService(new InMemoryWordPairRepository(), new InMemoryWordAssignmentRepository());
    }
}
