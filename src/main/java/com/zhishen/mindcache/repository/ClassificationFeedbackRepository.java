package com.zhishen.mindcache.repository;

import com.zhishen.mindcache.model.entity.ClassificationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * ClassificationFeedback 数据访问层。
 * <p>用于人在回路（Human-in-the-loop）分类修正记录存储与 few-shot 示例查询。
 */
@Repository
public interface ClassificationFeedbackRepository extends JpaRepository<ClassificationFeedback, UUID> {

    /** 取最近 N 条修正记录，用于 few-shot 示例注入 */
    List<ClassificationFeedback> findTop5ByOrderByCreatedAtDesc();
}
