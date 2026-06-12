package com.zhishen.mindcache.service;

import com.zhishen.mindcache.model.entity.ItemTag;
import com.zhishen.mindcache.model.entity.KnowledgeItem;
import com.zhishen.mindcache.model.entity.Tag;
import com.zhishen.mindcache.repository.ItemTagRepository;
import com.zhishen.mindcache.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 标签管理服务。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>查找或创建标签</li>
 *   <li>同步知识条目的标签关联（先删后建，统一维护 usage_count）</li>
 *   <li>标签列表（按热度降序）</li>
 * </ul>
 *
 * <h3>usage_count 一致性</h3>
 * usage_count 由 {@link #syncItemTags} 集中维护：删旧关联时 -1、建新关联时 +1。
 * {@link #findOrCreateTag} 是纯查/建方法，不触碰计数。
 */
@Service
@Transactional
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);

    private final TagRepository tagRepository;
    private final ItemTagRepository itemTagRepository;

    public TagService(TagRepository tagRepository, ItemTagRepository itemTagRepository) {
        this.tagRepository = tagRepository;
        this.itemTagRepository = itemTagRepository;
    }

    /**
     * 查找已有标签，不存在则创建。
     * <p>纯查/建，不修改 usage_count。计数由 {@link #syncItemTags} 集中维护。
     *
     * @param name 标签名
     * @return 已持久化的 Tag 实体
     */
    public Tag findOrCreateTag(String name) {
        return tagRepository.findByName(name)
                .orElseGet(() -> {
                    Tag newTag = new Tag();
                    newTag.setName(name);
                    newTag.setUsageCount(0);
                    newTag.setIsNew(true);
                    Tag saved = tagRepository.save(newTag);
                    log.info("New tag created: '{}'", name);
                    return saved;
                });
    }

    /**
     * 同步知识条目的标签关联（先按来源删旧，再建新）。
     * <p>usage_count 在此集中维护：删旧 -1、建新 +1，保证计数始终等于实际关联数。
     *
     * @param item     知识条目
     * @param tagNames 新标签名列表
     * @param source   来源（AI / USER）
     */
    public void syncItemTags(KnowledgeItem item, List<String> tagNames, String source) {
        // 清洗输入：null 兜底、去空、trim、去重
        List<String> cleaned = (tagNames == null) ? List.of() : tagNames.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        // 删除同来源旧关联，递减 usage_count
        List<ItemTag> existing = itemTagRepository.findByItemId(item.getId());
        for (ItemTag it : existing) {
            if (source.equals(it.getSource())) {
                Tag oldTag = it.getTag();
                int prev = oldTag.getUsageCount() != null ? oldTag.getUsageCount() : 0;
                oldTag.setUsageCount(Math.max(0, prev - 1));
                tagRepository.save(oldTag);
                itemTagRepository.delete(it);
            }
        }

        // 创建新关联，递增 usage_count
        for (String name : cleaned) {
            Tag tag = findOrCreateTag(name);
            int prev = tag.getUsageCount() != null ? tag.getUsageCount() : 0;
            tag.setUsageCount(prev + 1);
            tagRepository.save(tag);
            ItemTag itemTag = new ItemTag(item, tag, source);
            itemTagRepository.save(itemTag);
        }

        log.info("Synced {} tags (source={}) for item {}", cleaned.size(), source, item.getId());
    }

    /**
     * 获取全部标签，按使用次数降序排列。
     */
    @Transactional(readOnly = true)
    public List<Tag> findAllOrderByUsage() {
        return tagRepository.findAll(Sort.by(Sort.Direction.DESC, "usageCount"));
    }

    /**
     * 读取某个知识条目的所有标签名（用于反馈记录 / 展示）。
     */
    @Transactional(readOnly = true)
    public List<String> getItemTagNames(UUID itemId) {
        return itemTagRepository.findByItemId(itemId).stream()
                .map(it -> it.getTag().getName())
                .toList();
    }

    /**
     * 递减知识条目所有关联标签的 usage_count（删除笔记前调用）。
     * <p>item_tags 由 DB 级联删除，此方法手动维护计数一致性。
     */
    public void decrementTagsForItem(UUID itemId) {
        List<ItemTag> itemTags = itemTagRepository.findByItemId(itemId);
        for (ItemTag it : itemTags) {
            Tag tag = it.getTag();
            int prev = tag.getUsageCount() != null ? tag.getUsageCount() : 0;
            tag.setUsageCount(Math.max(0, prev - 1));
            tagRepository.save(tag);
        }
        log.debug("Decremented usage_count for {} tags of item {}", itemTags.size(), itemId);
    }
}
