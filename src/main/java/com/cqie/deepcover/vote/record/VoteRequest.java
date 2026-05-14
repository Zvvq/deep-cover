package com.cqie.deepcover.vote.record;

/**
 * 真人玩家提交投票时的请求体。
 *
 * @param targetPlayerId 要投给的玩家 ID
 */
public record VoteRequest(String targetPlayerId) {
}
