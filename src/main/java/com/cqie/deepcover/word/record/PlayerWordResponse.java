package com.cqie.deepcover.word.record;

/**
 * 玩家自己的私有关键词响应。
 *
 * <p>接口只返回当前请求玩家的词，不把全房间词语放进 RoomSnapshot。</p>
 */
public record PlayerWordResponse(
        String playerId,
        Integer number,
        String color,
        String word
) {
}
