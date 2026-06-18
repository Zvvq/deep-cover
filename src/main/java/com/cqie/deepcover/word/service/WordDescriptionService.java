package com.cqie.deepcover.word.service;

import com.cqie.deepcover.chat.enums.RoomEventType;
import com.cqie.deepcover.chat.interfaces.ChatEventPublisher;
import com.cqie.deepcover.chat.record.RoomEvent;
import com.cqie.deepcover.redis.lock.NoopRoomLockExecutor;
import com.cqie.deepcover.redis.lock.RoomLockExecutor;
import com.cqie.deepcover.room.enums.GameMode;
import com.cqie.deepcover.room.enums.RoomErrorCode;
import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.room.exception.RoomException;
import com.cqie.deepcover.room.record.Player;
import com.cqie.deepcover.room.record.PlayerSnapshot;
import com.cqie.deepcover.room.record.RoomSnapshot;
import com.cqie.deepcover.room.service.RoomService;
import com.cqie.deepcover.vote.service.VoteService;
import com.cqie.deepcover.word.interfaces.WordDescriptionRepository;
import com.cqie.deepcover.word.record.WordDescription;
import com.cqie.deepcover.word.record.WordDescriptionEntry;
import com.cqie.deepcover.word.record.WordDescriptionRequest;
import com.cqie.deepcover.word.record.WordDescriptionResult;
import com.cqie.deepcover.word.record.WordDescriptionSnapshot;
import com.cqie.deepcover.word.record.WordDescriptionSubmittedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关键词卧底描述阶段服务。
 *
 * <p>它只负责描述顺序、描述记录和“全员描述完成后进入投票”。具体投票校验和结算仍由 vote 模块处理。</p>
 */
@Service
public class WordDescriptionService {
    private static final int MAX_DESCRIPTION_LENGTH = 200;

    private final RoomService roomService;
    private final WordGameService wordGameService;
    private final WordDescriptionRepository wordDescriptionRepository;
    private final VoteService voteService;
    private final ChatEventPublisher chatEventPublisher;
    private final Clock clock;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final RoomLockExecutor roomLockExecutor;

    public WordDescriptionService(
            RoomService roomService,
            WordGameService wordGameService,
            WordDescriptionRepository wordDescriptionRepository,
            VoteService voteService,
            ChatEventPublisher chatEventPublisher,
            Clock clock
    ) {
        this(roomService, wordGameService, wordDescriptionRepository, voteService, chatEventPublisher, clock,
                event -> {
                }, new NoopRoomLockExecutor());
    }

    public WordDescriptionService(
            RoomService roomService,
            WordGameService wordGameService,
            WordDescriptionRepository wordDescriptionRepository,
            VoteService voteService,
            ChatEventPublisher chatEventPublisher,
            Clock clock,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this(roomService, wordGameService, wordDescriptionRepository, voteService, chatEventPublisher, clock,
                applicationEventPublisher, new NoopRoomLockExecutor());
    }

    @Autowired
    public WordDescriptionService(
            RoomService roomService,
            WordGameService wordGameService,
            WordDescriptionRepository wordDescriptionRepository,
            VoteService voteService,
            ChatEventPublisher chatEventPublisher,
            Clock clock,
            ApplicationEventPublisher applicationEventPublisher,
            RoomLockExecutor roomLockExecutor
    ) {
        this.roomService = roomService;
        this.wordGameService = wordGameService;
        this.wordDescriptionRepository = wordDescriptionRepository;
        this.voteService = voteService;
        this.chatEventPublisher = chatEventPublisher;
        this.clock = clock;
        this.applicationEventPublisher = applicationEventPublisher;
        this.roomLockExecutor = roomLockExecutor;
    }

    /**
     * 真人玩家提交描述。只有当前序号对应的玩家能提交。
     */
    public synchronized WordDescriptionResult submitDescription(
            String roomCode,
            String playerToken,
            WordDescriptionRequest request
    ) {
        return roomLockExecutor.execute(roomCode, () -> submitDescriptionLocked(roomCode, playerToken, request));
    }

    private WordDescriptionResult submitDescriptionLocked(
            String roomCode,
            String playerToken,
            WordDescriptionRequest request
    ) {
        Player player = roomService.requireWordParticipant(roomCode, playerToken);
        return submitDescriptionByPlayerId(roomCode, player.id(), request == null ? null : request.content());
    }

    /**
     * 系统内部提交描述，预留给后续 AI agent 接入使用。
     */
    public synchronized WordDescriptionResult submitSystemDescription(String roomCode, String playerId, String content) {
        return roomLockExecutor.execute(roomCode, () -> submitSystemDescriptionLocked(roomCode, playerId, content));
    }

    private WordDescriptionResult submitSystemDescriptionLocked(String roomCode, String playerId, String content) {
        return submitDescriptionByPlayerId(roomCode, playerId, content);
    }

    /**
     * 查询当前描述阶段状态。玩家 token 只用于确认请求者是本房间存活玩家。
     */
    public synchronized WordDescriptionSnapshot snapshot(String roomCode, String playerToken) {
        roomService.requireWordParticipant(roomCode, playerToken);
        return snapshot(roomService.snapshot(roomCode));
    }

    /**
     * Agent 内部接口查询描述状态时使用，不需要真人 token。
     */
    public synchronized WordDescriptionSnapshot systemSnapshot(String roomCode) {
        return snapshot(requireDescribingWordRoom(roomCode));
    }

    private WordDescriptionResult submitDescriptionByPlayerId(String roomCode, String playerId, String content) {
        String normalizedContent = normalizeContent(content);
        RoomSnapshot room = requireDescribingWordRoom(roomCode);
        PlayerSnapshot player = findAlivePlayer(room, playerId);
        int roundNumber = currentRound(roomCode);
        PlayerSnapshot currentPlayer = currentPlayer(room, roundNumber);

        if (currentPlayer == null || !currentPlayer.id().equals(player.id())) {
            throw new RoomException(RoomErrorCode.WORD_DESCRIPTION_TURN_MISMATCH, "It is not this player's turn.");
        }
        if (wordDescriptionRepository.findByRoomCodeAndRoundAndPlayerId(roomCode, roundNumber, player.id()).isPresent()) {
            throw new RoomException(RoomErrorCode.WORD_DESCRIPTION_TURN_MISMATCH, "Player already described this round.");
        }

        WordDescription description = new WordDescription(
                roomCode,
                roundNumber,
                player.id(),
                player.number(),
                player.color(),
                player.type(),
                normalizedContent,
                clock.instant()
        );
        wordDescriptionRepository.save(description);

        WordDescriptionSnapshot afterSubmit = snapshot(room);
        WordDescriptionEntry entry = WordDescriptionEntry.from(description);
        chatEventPublisher.publish(roomCode, new RoomEvent(RoomEventType.WORD_DESCRIPTION_SUBMITTED, entry));
        applicationEventPublisher.publishEvent(new WordDescriptionSubmittedEvent(roomCode, roundNumber, entry));

        boolean votingStarted = false;
        if (afterSubmit.roundComplete()) {
            voteService.startVoting(roomCode);
            votingStarted = true;
        }

        return new WordDescriptionResult(roomCode, roundNumber, entry, afterSubmit, votingStarted);
    }

    private RoomSnapshot requireDescribingWordRoom(String roomCode) {
        RoomSnapshot room = roomService.snapshot(roomCode);
        if (room.gameMode() != GameMode.WORD_UNDERCOVER) {
            throw new RoomException(RoomErrorCode.ROOM_MODE_NOT_SUPPORTED, "Room is not word undercover mode.");
        }
        if (room.status() != RoomStatus.DESCRIBING) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_DESCRIBING, "Room is not describing.");
        }
        return room;
    }

    private WordDescriptionSnapshot snapshot(RoomSnapshot room) {
        int roundNumber = currentRound(room.roomCode());
        List<WordDescriptionEntry> descriptions = wordDescriptionRepository
                .findByRoomCodeAndRound(room.roomCode(), roundNumber)
                .stream()
                .map(WordDescriptionEntry::from)
                .toList();
        PlayerSnapshot currentPlayer = currentPlayer(room, roundNumber);
        return new WordDescriptionSnapshot(
                room.roomCode(),
                roundNumber,
                currentPlayer == null ? null : currentPlayer.id(),
                currentPlayer == null ? null : currentPlayer.number(),
                currentPlayer == null,
                descriptions
        );
    }

    private PlayerSnapshot currentPlayer(RoomSnapshot room, int roundNumber) {
        Set<String> describedPlayerIds = wordDescriptionRepository
                .findByRoomCodeAndRound(room.roomCode(), roundNumber)
                .stream()
                .map(WordDescription::playerId)
                .collect(Collectors.toSet());
        return room.players().stream()
                .filter(PlayerSnapshot::alive)
                .filter(player -> !describedPlayerIds.contains(player.id()))
                .min(Comparator.comparing(PlayerSnapshot::number))
                .orElse(null);
    }

    private PlayerSnapshot findAlivePlayer(RoomSnapshot room, String playerId) {
        PlayerSnapshot player = room.players().stream()
                .filter(candidate -> candidate.id().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new RoomException(RoomErrorCode.PLAYER_NOT_FOUND, "Player not found."));
        if (!player.alive()) {
            throw new RoomException(RoomErrorCode.PLAYER_NOT_ALIVE, "Player is not alive.");
        }
        return player;
    }

    private int currentRound(String roomCode) {
        int roundNumber = wordGameService.currentRound(roomCode);
        if (roundNumber <= 0) {
            throw new RoomException(RoomErrorCode.WORD_NOT_ASSIGNED, "Word round is not assigned.");
        }
        return roundNumber;
    }

    private String normalizeContent(String content) {
        if (content == null || content.isBlank()) {
            throw new RoomException(RoomErrorCode.INVALID_WORD_DESCRIPTION, "Description cannot be empty.");
        }
        String trimmed = content.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new RoomException(RoomErrorCode.INVALID_WORD_DESCRIPTION, "Description is too long.");
        }
        return trimmed;
    }
}
