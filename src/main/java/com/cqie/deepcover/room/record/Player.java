package com.cqie.deepcover.room.record;

import com.cqie.deepcover.room.enums.PlayerType;

/**
 * 房间内玩家的最小身份信息。
 *
 * <p>token 只给服务端识别浏览器身份，不能展示在页面上。number/color
 * 会在 game 模块开局分配，room 模块只先保留字段。</p>
 */
public record Player(
        String id,
        String token,
        Integer number,
        String color,
        PlayerType type,
        boolean alive,
        boolean host
) {
    /**
     * 创建房主玩家。房主离开时会触发房间销毁。
     */
    public static Player humanHost(String id, String token) {
        return new Player(id, token, null, null, PlayerType.HUMAN, true, true);
    }

    /**
     * 创建普通真人玩家。AI 玩家后续由 game 模块统一注入。
     */
    public static Player human(String id, String token) {
        return new Player(id, token, null, null, PlayerType.HUMAN, true, false);
    }

    /**
     * 创建 AI 卧底玩家。
     *
     * <p>当前先把 AI 作为普通房间玩家放入列表，后续接入 Python Agent 时可以通过 type=AI 找到它。</p>
     */
    public static Player ai(String id, String token) {
        return new Player(id, token, null, null, PlayerType.AI, true, false);
    }

    /**
     * record 本身不可变，淘汰玩家时返回一个新的玩家对象交给 Room 替换。
     */
    public Player eliminate() {
        return new Player(id, token, number, color, type, false, host);
    }
}
