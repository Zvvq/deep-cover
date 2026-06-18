package com.cqie.deepcover.room.model;

import com.cqie.deepcover.room.enums.GameMode;
import com.cqie.deepcover.room.enums.PlayerType;
import com.cqie.deepcover.room.enums.RoomStatus;
import com.cqie.deepcover.room.record.Player;
import com.cqie.deepcover.room.record.RoomDocument;
import com.cqie.deepcover.topic.record.TopicSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 房间聚合对象，保存 room 模块自己的核心状态。
 *
 * <p>Room 只负责玩家列表和房间生命周期。聊天、投票、AI 决策都通过服务层组合，
 * 避免把所有玩法逻辑塞进这个对象。</p>
 */
public class Room {
    private static final List<String> PLAYER_COLORS = List.of(
            "RED",
            "BLUE",
            "GREEN",
            "YELLOW",
            "PURPLE",
            "ORANGE",
            "CYAN",
            "PINK",
            "GRAY",
            "BROWN",
            "LIME",
            "TEAL"
    );

    private final String roomCode;
    private final String hostPlayerId;
    private final GameMode gameMode;
    private final List<Player> players;
    private RoomStatus status;
    private TopicSnapshot topic;

    private Room(String roomCode, Player host, GameMode gameMode) {
        this.roomCode = roomCode;
        this.hostPlayerId = host.id();
        this.gameMode = gameMode == null ? GameMode.CHAT_UNDERCOVER : gameMode;
        this.players = new ArrayList<>();
        this.players.add(host);
        this.status = RoomStatus.WAITING;
    }

    public static Room createWaiting(String roomCode, Player host) {
        return createWaiting(roomCode, host, GameMode.CHAT_UNDERCOVER);
    }

    public static Room createWaiting(String roomCode, Player host, GameMode gameMode) {
        return new Room(roomCode, host, gameMode);
    }

    /**
     * 从持久化文档重建房间对象，仅供仓库层反序列化使用。
     *
     * <p>直接使用存储的完整玩家列表替换内部列表，并按存储的状态字段还原生命周期阶段。</p>
     */
    public static Room reconstitute(RoomDocument doc) {
        Player host = doc.players().stream()
                .filter(Player::host)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No host player found in document."));

        Room room = new Room(doc.roomCode(), host, doc.gameMode());
        // 用文档中的完整玩家列表替换构造器自动添加的单一房主
        room.players.clear();
        room.players.addAll(doc.players());
        room.status = doc.status();
        if (doc.topic() != null) {
            room.topic = doc.topic();
        }
        return room;
    }

    /**
     * 生成用于持久化的数据传输对象。
     */
    public RoomDocument toDocument() {
        return new RoomDocument(
                roomCode,
                hostPlayerId,
                gameMode,
                new ArrayList<>(players),
                status,
                topic
        );
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

    public GameMode gameMode() {
        return gameMode;
    }

    public List<Player> players() {
        return Collections.unmodifiableList(players);
    }

    public TopicSnapshot topic() {
        return topic;
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(String playerId) {
        players.removeIf(player -> player.id().equals(playerId));
    }

    /**
     * 开局时随机打乱玩家顺序，并按打乱后的顺序分配前端展示用的序号和颜色。
     */
    public void assignPlayerIdentities(Random random) {
        if (players.size() > PLAYER_COLORS.size()) {
            throw new IllegalStateException("Player color pool is not enough.");
        }

        Collections.shuffle(players, random);
        for (int i = 0; i < players.size(); i++) {
            players.set(i, players.get(i).assignIdentity(i + 1, PLAYER_COLORS.get(i)));
        }
    }

    public void assignTopic(TopicSnapshot topic) {
        this.topic = topic;
    }

    public void markChatting() {
        status = RoomStatus.CHATTING;
    }

    public void markDescribing() {
        status = RoomStatus.DESCRIBING;
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
