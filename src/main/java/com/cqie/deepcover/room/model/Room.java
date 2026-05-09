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
 * <p>这里先只处理等待、开始、离开这些房间生命周期规则。聊天记录、投票、
 * AI 玩家注入等会放到后续 game/chat 模块里，避免 Room 变成大杂烩。</p>
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
        // 只允许 Room 自己修改玩家列表，外部只能拿只读视图。
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

    public void markDestroyed() {
        status = RoomStatus.DESTROYED;
    }

    public Optional<Player> findPlayerByToken(String token) {
        // token 是服务端识别浏览器身份的凭证，不会通过快照暴露给其他玩家。
        return players.stream()
                .filter(player -> player.token().equals(token))
                .findFirst();
    }

    public long humanPlayerCount() {
        return players.stream()
                .filter(player -> player.type() == PlayerType.HUMAN)
                .count();
    }
}
