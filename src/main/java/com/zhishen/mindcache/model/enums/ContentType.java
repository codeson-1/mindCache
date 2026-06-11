package com.zhishen.mindcache.model.enums;

/**
 * 知识条目的输入模态。
 * <ul>
 *   <li>{@code TEXT}  — 手动文字输入</li>
 *   <li>{@code AUDIO} — 语音录入（经 qwen3-asr-flash 转写）</li>
 *   <li>{@code IMAGE} — 图片上传（经 qwen-plus OCR + 视觉描述）</li>
 * </ul>
 */
public enum ContentType {
    /** 手动文字输入 */
    TEXT,
    /** 语音录入 */
    AUDIO,
    /** 图片上传 */
    IMAGE
}
