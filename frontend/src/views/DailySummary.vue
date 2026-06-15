<template>
  <div class="page-container">
    <!-- 摘要控制栏 -->
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; align-items: center; gap: 12px">
          <span>📋 每日知识摘要</span>
          <el-tag type="info" effect="plain" size="small">AI 三明治摘要法</el-tag>
          <span class="time-meta">{{ todayDate }}</span>
        </div>
      </template>

      <div class="summary-intro">
        <p>AI 会自动分析过去 24 小时录入的知识碎片，生成有洞察的日报摘要——包括重点发现、知识关联、待深入主题和行动建议。</p>
        <el-button
          type="primary"
          size="large"
          :loading="generating"
          :disabled="generating"
          @click="startGeneration"
        >
          <el-icon><MagicStick /></el-icon>
          {{ generating ? 'AI 正在分析…' : '生成今日摘要' }}
        </el-button>
      </div>
    </el-card>

    <!-- 摘要内容区 -->
    <el-card shadow="never" class="summary-card" v-if="content || generating">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span>🧠 {{ generating ? 'AI 思考中…' : '今日摘要' }}</span>
          <div v-if="!generating && content">
            <el-button text size="small" @click="copyContent">
              <el-icon><CopyDocument /></el-icon> 复制
            </el-button>
            <el-button text size="small" @click="resetAndRegenerate">
              <el-icon><Refresh /></el-icon> 重新生成
            </el-button>
          </div>
        </div>
      </template>

      <!-- 流式内容渲染为 Markdown 风格 -->
      <div v-if="content" class="summary-content markdown-body" v-html="renderedContent" />

      <!-- 加载骨架 -->
      <div v-if="generating && !content" class="generating-placeholder">
        <el-skeleton :rows="10" animated />
        <div class="generating-hint">AI 正在分析你的知识碎片…</div>
      </div>

      <!-- 生成中闪烁光标 -->
      <span v-if="generating && content" class="typing-cursor">|</span>
    </el-card>

    <!-- 无内容 -->
    <el-card shadow="never" class="summary-card" v-if="!content && !generating && !firstVisit">
      <el-empty description="还没有生成摘要" />
      <div class="text-center" style="margin-top: -16px">
        <el-button type="primary" @click="startGeneration">生成摘要</el-button>
      </div>
    </el-card>

    <div v-if="!content && !generating && firstVisit" />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { getDailySummaryUrl } from '@/api'
import { ElMessage } from 'element-plus'

const content = ref('')
const generating = ref(false)
const firstVisit = ref(true)
let eventSource = null

const todayDate = computed(() => {
  return new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  })
})

/** 简单 Markdown → HTML 渲染 */
const renderedContent = computed(() => {
  if (!content.value) return ''
  let html = content.value
    // 标题
    .replace(/^### (.+)$/gm, '<h4>$1</h4>')
    .replace(/^## (.+)$/gm, '<h3>$1</h3>')
    .replace(/^# (.+)$/gm, '<h2>$1</h2>')
    // 粗体
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    // 列表
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>')
    // 数字列表
    .replace(/^\d+\.\s(.+)$/gm, '<li>$1</li>')
    // 段落
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br>')

  return '<p>' + html + '</p>'
})

function startGeneration() {
  firstVisit.value = false
  content.value = ''
  generating.value = true

  // 关闭旧的连接
  if (eventSource) {
    eventSource.close()
  }

  const url = getDailySummaryUrl()
  eventSource = new EventSource(url)

  eventSource.onmessage = (event) => {
    if (event.data) {
      content.value += event.data
    }
  }

  eventSource.onerror = () => {
    eventSource.close()
    generating.value = false
    if (!content.value) {
      ElMessage.warning('摘要生成遇到问题，请重试')
    }
  }

  // 监听特定事件类型（Flux 默认发送 onNext 作为 message）
  eventSource.addEventListener('error', () => {
    eventSource.close()
    generating.value = false
  })
}

function resetAndRegenerate() {
  content.value = ''
  if (eventSource) eventSource.close()
  startGeneration()
}

async function copyContent() {
  try {
    await navigator.clipboard.writeText(content.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}
</script>

<style scoped>
.summary-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}
.summary-intro p {
  margin: 0;
  flex: 1;
  min-width: 250px;
  color: var(--el-text-color-secondary);
  font-size: 0.9rem;
  line-height: 1.6;
}

.summary-card {
  margin-top: 16px;
}

.summary-content {
  line-height: 1.8;
  color: var(--el-text-color-regular);
}

/* Markdown 风格 */
.markdown-body :deep(h2) {
  font-size: 1.2rem;
  margin: 20px 0 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid var(--el-border-color-light);
}
.markdown-body :deep(h3) {
  font-size: 1.05rem;
  margin: 16px 0 8px;
  color: var(--el-color-primary);
}
.markdown-body :deep(h4) {
  font-size: 0.95rem;
  margin: 12px 0 6px;
  color: var(--el-text-color-primary);
}
.markdown-body :deep(ul) {
  padding-left: 20px;
  margin: 8px 0;
}
.markdown-body :deep(li) {
  margin-bottom: 4px;
}
.markdown-body :deep(strong) {
  color: var(--el-color-primary-dark-2);
}
.markdown-body :deep(p) {
  margin: 4px 0;
}

.generating-placeholder {
  padding: 16px 0;
}
.generating-hint {
  text-align: center;
  color: var(--el-text-color-secondary);
  margin-top: 12px;
  font-size: 0.9rem;
}

.typing-cursor {
  display: inline-block;
  animation: blink 1s step-end infinite;
  color: var(--el-color-primary);
  font-weight: bold;
  font-size: 1.1rem;
}

@keyframes blink {
  50% { opacity: 0; }
}

.text-center {
  text-align: center;
}
</style>
