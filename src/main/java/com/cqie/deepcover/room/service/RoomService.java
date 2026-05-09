package com.cqie.deepcover.room.service;

import com.cqie.deepcover.room.enums.RoomErrorCode;
import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.room.exception.RoomException;
import com.cqie.deepcover.room.interfaces.RoomRepository;
import com.cqie.deepcover.room.model.Room;
import com.cqie.deepcover.room.record.Player;
import com.cqie.deepcover.room.record.PlayerSnapshot;
import com.cqie.deepcover.room.record.RoomCreateResult;
import com.cqie.deepcover.room.record.RoomJoinResult;
import com.cqie.deepcover.room.record.RoomSnapshot;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * room 模块的应用服务，集中处理房间生命周期和权限校验。
 *
 * <p>Controller 不直接操作 Room，后续 game/chat 模块也应该优先通过这里获取
 * 房间状态，避免规则散落到多个入口。</p>
 */
@Service
public class RoomService {
    private static final int MAX_HUMAN_PLAYERS = 8;
    private static final int MIN_HUMAN_PLAYERS_TO_START = 2;

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    /**
     * 创建房间时，自动生成一个 host 玩家，并返回房间快照和玩家凭证。
     * @return 创建房间的响应
     */
    public synchronized RoomCreateResult createRoom() {
        String roomCode = roomRepository.nextRoomCode();
        Player host = Player.humanHost(nextId(), nextToken());
        Room room = Room.createWaiting(roomCode, host);
        roomRepository.save(room);
        return new RoomCreateResult(roomCode, host.id(), host.token(), snapshot(room));
    }

    /**
     * 加入房间时，先检查房间状态和人数限制，再创建一个玩家并加入房间。开局后不允许加入，
     * @param roomCode 房间码
     * @return 加入房间的响应
     */
    public synchronized RoomJoinResult joinRoom(String roomCode) {
        Room room = findRoom(roomCode);
        // 只有等待中的房间能加入；开局后加入会破坏匿名分配和游戏平衡。
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

    /**
     * 获取房间状态
     * @param roomCode 房间码
     * @return 房间快照
     */
    public RoomSnapshot snapshot(String roomCode) {
        return snapshot(findRoom(roomCode));
    }

    /**
     * 开局
     * @param roomCode 房间码
     * @param playerToken 玩家凭证
     * @return 房间快照
     */
    public synchronized RoomSnapshot startRoom(String roomCode, String playerToken) {
        Room room = findRoom(roomCode);
        Player requester = findPlayer(room, playerToken);
        // start 是房主动作，先用 token 找到玩家，再判断 host 标记。
        if (!requester.host()) {
            throw new RoomException(RoomErrorCode.FORBIDDEN, "Only the host can start the room.");
        }
        if (room.humanPlayerCount() < MIN_HUMAN_PLAYERS_TO_START) {
            throw new RoomException(RoomErrorCode.NOT_ENOUGH_PLAYERS, "At least two human players are required.");
        }
        room.markChatting();
        roomRepository.save(room);
        return snapshot(room);
    }

    /**
     * 离开房间，房主离开时房间会销毁，其他玩家离开更新状态
     * @param roomCode 房间码
     * @param playerToken 玩家凭证
     * @return 房间快照
     */
    public synchronized RoomSnapshot leaveRoom(String roomCode, String playerToken) {
        Room room = findRoom(roomCode);
        Player player = findPlayer(room, playerToken);
        if (player.host()) {
            // 用户明确要求：房主离开时房间直接销毁，而不是转让房主。
            room.markDestroyed();
            RoomSnapshot destroyedSnapshot = snapshot(room);
            roomRepository.deleteByCode(roomCode);
            return destroyedSnapshot;
        }

        room.removePlayer(player.id());
        roomRepository.save(room);
        return snapshot(room);
    }

    /**
     * 根据房间码查找房间
     * @param roomCode 房间码
     * @return 房间
     */
    private Room findRoom(String roomCode) {
        return roomRepository.findByCode(roomCode)
                .orElseThrow(() -> new RoomException(RoomErrorCode.ROOM_NOT_FOUND, "Room not found."));
    }

    /**
     * 根据房间和玩家凭证查找玩家
     * @param room 房间
     * @param playerToken 玩家凭证
     * @return 玩家
     */
    private Player findPlayer(Room room, String playerToken) {
        return room.findPlayerByToken(playerToken)
                .orElseThrow(() -> new RoomException(RoomErrorCode.PLAYER_NOT_FOUND, "Player not found."));
    }

    /**
     * 将 Room 转换为 RoomSnapshot，后者是一个只读视图，用于返回给客户端。RoomSnapshot 中不包含玩家 token，
     * @param room 房间
     * @return 房间快照
     */
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

    /**
     * 生成一个随机的玩家 ID
     * @return 玩家 ID
     */
    private String nextId() {
        return "player-" + UUID.randomUUID();
    }

    /**
     * 生成一个随机的玩家凭证
     * @return 玩家凭证
     */
    private String nextToken() {
        return UUID.randomUUID().toString();
    }
}
