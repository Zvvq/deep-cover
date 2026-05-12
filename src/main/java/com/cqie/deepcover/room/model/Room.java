package com.cqie.deepcover.room.model;

import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.room.record.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 房间聚合对象，保存 room 模块自己的核心状态。
 *
 * <p>Room 只负责玩家列表和房间生命周期。聊天、投票、AI 决策都通过服务层组合，
 * 避免把所有玩法逻辑塞进这个对象。</p>
 */
public class Room {
    private final String roomCode;
    private final String hostPlayerId;
    private final List<Player> players;
    private RoomStatus status;

    private Room(String roomCode, Player host) {
        this.roomCode = roomCode;
        this.hostPlayerId = host.id();
        this.players = new ArrayList<>();
        this.players.add(host);
        this.status = RoomStatus.WAITING;
    }

    public static Room createWaiting(String roomCode, Player host) {
        return new Room(roomCode, host);
    }

    public String roomCode() {
        return roomCode;
    }

    public String hostPlayerId() {
        return hostPlayerId;
    }

    public RoomStatus status() {
        return status;
    }

    public List<Player> players() {
        return Collections.unmodifiableList(players);
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(String playerId) {
        players.removeIf(player -> player.id().equals(playerId));
    }

    public void markChatting() {
        status = RoomStatus.CHATTING;
    }

    public void markVoting() {
        status = RoomStatus.VOTING;
    }

    public void markEnded() {
        status = RoomStatus.ENDED;
    }

    public void markDestroyed() {
        status = RoomStatus.DESTROYED;
    }

    public Optional<Player> findPlayerByToken(String token) {
        return players.stream()
                .filter(player -> player.token().equals(token))
                .findFirst();
    }

    public Optional<Player> findPlayerById(String playerId) {
        return players.stream()
                .filter(player -> player.id().equals(playerId))
                .findFirst();
    }

    /**
     * Player 是不可变 record，所以这里用新对象替换原玩家。
     */
    public void eliminatePlayer(String playerId) {
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (player.id().equals(playerId)) {
                players.set(i, player.eliminate());
                return;
            }
        }
    }

    public long humanPlayerCount() {
        return players.stream()
                .filter(player -> player.type() == PlayerType.HUMAN)
                .count();
    }

    public long aiPlayerCount() {
        return players.stream()
                .filter(player -> player.type() == PlayerType.AI)
                .count();
    }

    public long aliveHumanPlayerCount() {
        return players.stream()
                .filter(player -> player.alive() && player.type() == PlayerType.HUMAN)
                .count();
    }

    public long aliveAiPlayerCount() {
        return players.stream()
                .filter(player -> player.alive() && player.type() == PlayerType.AI)
                .count();
    }
}
