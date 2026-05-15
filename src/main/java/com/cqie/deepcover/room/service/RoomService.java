package com.cqie.deepcover.room.service;

import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.room.enums.RoomErrorCode;
import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.room.exception.RoomException;
import com.cqie.deepcover.room.interfaces.RoomRepository;
import com.cqie.deepcover.room.model.Room;
import com.cqie.deepcover.room.record.Player;
import com.cqie.deepcover.room.record.PlayerSnapshot;
import com.cqie.deepcover.room.record.RoomCreateResult;
import com.cqie.deepcover.room.record.RoomDestroyedEvent;
import com.cqie.deepcover.room.record.RoomJoinResult;
import com.cqie.deepcover.room.record.RoomStartedEvent;
import com.cqie.deepcover.room.record.RoomSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * room 模块的应用服务，集中处理房间生命周期和玩家身份校验。
 *
 * <p>其他模块不要直接操作 RoomRepository，而是通过这里的校验方法进入房间状态。
 * 这样投票、聊天、AI 决策都不会绕过同一套房间规则。</p>
 */
@Service
public class RoomService {
    private static final int MAX_HUMAN_PLAYERS = 8;
    private static final int MIN_HUMAN_PLAYERS_TO_START = 2;
    private static final int DEFAULT_AI_UNDERCOVER_COUNT = 1;

    private final RoomRepository roomRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RoomService(RoomRepository roomRepository) {
        this(roomRepository, event -> {
        });
    }

    @Autowired
    public RoomService(RoomRepository roomRepository, ApplicationEventPublisher eventPublisher) {
        this.roomRepository = roomRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 创建房间时自动生成房主玩家，并返回房间快照和房主 token。
     */
    public synchronized RoomCreateResult createRoom() {
        String roomCode = roomRepository.nextRoomCode();
        Player host = Player.humanHost(nextId(), nextToken());
        Room room = Room.createWaiting(roomCode, host);
        roomRepository.save(room);
        return new RoomCreateResult(roomCode, host.id(), host.token(), snapshot(room));
    }

    /**
     * 等待阶段允许真人加入；游戏开始后不能再加入。
     */
    public synchronized RoomJoinResult joinRoom(String roomCode) {
        Room room = findRoom(roomCode);
        if (room.status() != RoomStatus.WAITING) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_JOINABLE, "Room is not joinable.");
        }
        if (room.humanPlayerCount() >= MAX_HUMAN_PLAYERS) {
            throw new RoomException(RoomErrorCode.ROOM_FULL, "Room is full.");
        }

        Player player = Player.human(nextId(), nextToken());
        room.addPlayer(player);
        roomRepository.save(room);
        return new RoomJoinResult(roomCode, player.id(), player.token(), snapshot(room));
    }

    public RoomSnapshot snapshot(String roomCode) {
        return snapshot(findRoom(roomCode));
    }

    /**
     * 房主开始游戏：补齐默认 AI 玩家，进入聊天阶段，并发布房间开始事件。
     */
    public synchronized RoomSnapshot startRoom(String roomCode, String playerToken) {
        Room room = findRoom(roomCode);
        Player requester = findPlayer(room, playerToken);
        if (!requester.host()) {
            throw new RoomException(RoomErrorCode.FORBIDDEN, "Only the host can start the room.");
        }
        if (room.humanPlayerCount() < MIN_HUMAN_PLAYERS_TO_START) {
            throw new RoomException(RoomErrorCode.NOT_ENOUGH_PLAYERS, "At least two human players are required.");
        }
        addMissingAiUndercoverPlayers(room);
        room.markChatting();
        roomRepository.save(room);
        eventPublisher.publishEvent(new RoomStartedEvent(roomCode));
        return snapshot(room);
    }

    /**
     * 房主离开直接销毁房间，非房主离开则只移除自己。
     */
    public synchronized RoomSnapshot leaveRoom(String roomCode, String playerToken) {
        Room room = findRoom(roomCode);
        Player player = findPlayer(room, playerToken);
        if (player.host()) {
            room.markDestroyed();
            RoomSnapshot destroyedSnapshot = snapshot(room);
            eventPublisher.publishEvent(new RoomDestroyedEvent(roomCode, "Host left the room."));
            roomRepository.deleteByCode(roomCode);
            return destroyedSnapshot;
        }

        room.removePlayer(player.id());
        roomRepository.save(room);
        return snapshot(room);
    }

    /**
     * 聊天模块使用的真人玩家校验入口。
     */
    public synchronized Player requireChatParticipant(String roomCode, String playerToken) {
        Room room = findRoom(roomCode);
        if (room.status() != RoomStatus.CHATTING) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_CHATTING, "Room is not chatting.");
        }
        Player player = findPlayer(room, playerToken);
        requireAlive(player);
        return player;
    }

    /**
     * AI 发言使用的校验入口。AI 没有浏览器 token，所以系统内部用 playerId 校验。
     */
    public synchronized Player requireAliveAiChatParticipant(String roomCode, String playerId) {
        Room room = findRoom(roomCode);
        if (room.status() != RoomStatus.CHATTING) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_CHATTING, "Room is not chatting.");
        }
        Player player = findPlayerById(room, playerId);
        requireAi(player);
        requireAlive(player);
        return player;
    }

    /**
     * 真人投票使用的校验入口。
     */
    public synchronized Player requireVotingParticipant(String roomCode, String playerToken) {
        Room room = findRoom(roomCode);
        if (room.status() != RoomStatus.VOTING) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_VOTING, "Room is not voting.");
        }
        Player player = findPlayer(room, playerToken);
        requireAlive(player);
        return player;
    }

    /**
     * AI 投票使用的校验入口。
     */
    public synchronized Player requireAliveAiVotingParticipant(String roomCode, String playerId) {
        Room room = findRoom(roomCode);
        if (room.status() != RoomStatus.VOTING) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_VOTING, "Room is not voting.");
        }
        Player player = findPlayerById(room, playerId);
        requireAi(player);
        requireAlive(player);
        return player;
    }

    public synchronized RoomSnapshot markVoting(String roomCode) {
        Room room = findRoom(roomCode);
        room.markVoting();
        roomRepository.save(room);
        return snapshot(room);
    }

    public synchronized RoomSnapshot markChatting(String roomCode) {
        Room room = findRoom(roomCode);
        room.markChatting();
        roomRepository.save(room);
        return snapshot(room);
    }

    public synchronized RoomSnapshot markEnded(String roomCode) {
        Room room = findRoom(roomCode);
        room.markEnded();
        roomRepository.save(room);
        return snapshot(room);
    }

    public synchronized RoomSnapshot eliminatePlayer(String roomCode, String playerId) {
        Room room = findRoom(roomCode);
        Player player = findPlayerById(room, playerId);
        requireAlive(player);
        room.eliminatePlayer(playerId);
        roomRepository.save(room);
        return snapshot(room);
    }

    /**
     * AI 定时发言模块用这个方法找到所有正在聊天的房间。
     */
    public synchronized List<RoomSnapshot> snapshotsByStatus(RoomStatus status) {
        return roomRepository.findAll().stream()
                .filter(room -> room.status() == status)
                .map(this::snapshot)
                .toList();
    }

    private Room findRoom(String roomCode) {
        return roomRepository.findByCode(roomCode)
                .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_NOT_FOUND, "Room not found."));
    }

    private Player findPlayer(Room room, String playerToken) {
        return room.findPlayerByToken(playerToken)
                .orElseThrow(() -> new RoomException(RoomErrorCode.PLAYER_NOT_FOUND, "Player not found."));
    }

    private Player findPlayerById(Room room, String playerId) {
        return room.findPlayerById(playerId)
                .orElseThrow(() -> new RoomException(RoomErrorCode.PLAYER_NOT_FOUND, "Player not found."));
    }

    private void requireAlive(Player player) {
        if (!player.alive()) {
            throw new RoomException(RoomErrorCode.PLAYER_NOT_ALIVE, "Player is not alive.");
        }
    }

    private void requireAi(Player player) {
        if (player.type() != PlayerType.AI) {
            throw new RoomException(RoomErrorCode.PLAYER_NOT_FOUND, "AI player not found.");
        }
    }

    /**
     * 开局时补齐默认 AI 卧底。后续如果支持配置，可以把常量改成房间配置。
     */
    private void addMissingAiUndercoverPlayers(Room room) {
        long missingAiCount = DEFAULT_AI_UNDERCOVER_COUNT - room.aiPlayerCount();
        for (int i = 0; i < missingAiCount; i++) {
            room.addPlayer(Player.ai(nextAiId(), nextToken()));
        }
    }

    private RoomSnapshot snapshot(Room room) {
        return new RoomSnapshot(
                room.roomCode(),
                room.status(),
                room.hostPlayerId(),
                room.players().stream()
                        .map(PlayerSnapshot::from)
                        .toList()
        );
    }

    private String nextId() {
        return "player-" + UUID.randomUUID();
    }

    private String nextAiId() {
        return "ai-" + UUID.randomUUID();
    }

    private String nextToken() {
        return UUID.randomUUID().toString();
    }
}
