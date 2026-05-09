package com.cqie.deepcover.chat.record;

/**
 * 前端发送聊天消息时传入的数据。
 *
 * @param playerToken 玩家凭证，用来确认是谁在发言
 * @param content 聊天内容
 */
public record ChatMessageRequest(String playerToken, String content) {
}
