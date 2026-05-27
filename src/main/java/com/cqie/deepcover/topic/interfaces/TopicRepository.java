package com.cqie.deepcover.topic.interfaces;

import com.cqie.deepcover.topic.record.Topic;

import java.util.List;

/**
 * 话题仓库接口。
 */
public interface TopicRepository {
    List<Topic> findAll();
}
