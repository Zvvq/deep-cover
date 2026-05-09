package com.cqie.deepcover.room.record;

import com.cqie.deepcover.room.enums.PlayerType;

/**
 * 对前端可见的玩家信息。
 *
 * <p>这里故意不暴露 token，避免浏览器拿到其他玩家的身份凭证。</p>
 */
public record PlayerSnapshot(
        String id,
        PlayerType type,
        boolean alive,
        boolean host
) {
    /**
     * 从内部玩家对象转换成对外快照。
     */
    public static PlayerSnapshot from(Player player) {
        return new PlayerSnapshot(player.id(), player.type(), player.alive(), player.host());
    }
}
