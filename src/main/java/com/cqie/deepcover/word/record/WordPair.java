package com.cqie.deepcover.word.record;

/**
 * 关键词卧底模式的一组词。
 *
 * <p>civilianWord 给真人玩家，undercoverWord 给 AI 卧底玩家。</p>
 */
public record WordPair(
        String id,
        String civilianWord,
        String undercoverWord
) {
}
