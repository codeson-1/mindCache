package com.zhishen.mindcache.repository;

import com.zhishen.mindcache.model.entity.ClassificationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * ClassificationFeedback 数据访问层。
 * <p>用于人在回路（Human-in-the-loop）分类修正记录存储。
 * 后续（第4周）会补充按分类/来源等查询方法。
 */
@Repository
public interface ClassificationFeedbackRepository extends JpaRepository<ClassificationFeedback, UUID> {
}
