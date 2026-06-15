<template>
  <div class="page-container">
    <!-- 欢迎语 -->
    <el-row :gutter="20" class="welcome-row">
      <el-col :span="24">
        <el-card shadow="never" class="welcome-card">
          <div class="welcome-content">
            <div>
              <h2>🧠 欢迎回来</h2>
              <p class="welcome-desc">多模态碎片知识管家 — 文字、语音、图片统一录入，AI 智能检索</p>
            </div>
            <div class="welcome-actions">
              <el-button @click="$router.push('/summary')">
                <el-icon><DataAnalysis /></el-icon> 今日摘要
              </el-button>
              <el-button type="primary" @click="$router.push('/create')">
                <el-icon><EditPen /></el-icon> 快速记笔记
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-row :gutter="20" class="quick-actions">
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover" class="action-card" @click="$router.push('/create')">
          <div class="action-inner">
            <span class="action-icon">✍️</span>
            <div>
              <div class="action-title">文字录入</div>
              <div class="action-desc">直接输入笔记内容</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover" class="action-card" @click="$router.push('/voice')">
          <div class="action-inner">
            <span class="action-icon">🎙️</span>
            <div>
              <div class="action-title">语音录入</div>
              <div class="action-desc">录音转文字自动入库</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="hover" class="action-card" @click="$router.push('/image')">
          <div class="action-inner">
            <span class="action-icon">🖼️</span>
            <div>
              <div class="action-title">图片录入</div>
              <div class="action-desc">OCR + AI 视觉描述</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 统计卡片行 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon">📄</div>
            <div>
              <div class="stat-value">{{ stats.total }}</div>
              <div class="stat-label">笔记总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon">🏷️</div>
            <div>
              <div class="stat-value">{{ stats.tags }}</div>
              <div class="stat-label">标签数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon">📁</div>
            <div>
              <div class="stat-value">{{ stats.categories }}</div>
              <div class="stat-label">分类数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card" @click="$router.push('/summary')">
          <div class="stat-inner">
            <div class="stat-icon">📋</div>
            <div>
              <div class="stat-value">{{ stats.today }}</div>
              <div class="stat-label">今日新增</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近笔记 & 侧边栏 -->
    <el-row :gutter="20">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>📄 最近笔记</span>
              <el-button text type="primary" @click="$router.push('/notes')">
                查看全部 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>

          <div v-if="loading" class="loading-placeholder">
            <el-skeleton :rows="4" animated />
          </div>

          <div v-else-if="recentNotes.length === 0" class="empty-placeholder">
            <el-empty description="还没有笔记，快去录入第一条吧！" :image-size="100" />
          </div>

          <div v-else>
            <div
              v-for="item in recentNotes"
              :key="item.id"
              class="recent-item"
              @click="$router.push(`/notes/${item.id}`)"
            >
              <div class="recent-content">
                <el-tag size="small" :type="tagType(item.contentType)" effect="plain">
                  {{ contentTypeLabel(item.contentType) }}
                </el-tag>
                <span class="recent-text">{{ truncate(item.cleanContent, 90) }}</span>
              </div>
              <div class="recent-meta">
                <span class="time-meta">{{ formatTime(item.createdAt) }}</span>
                <el-tag v-if="item.autoCategory" size="small" effect="plain">{{ item.autoCategory }}</el-tag>
                <span v-if="item.summary" class="time-meta" style="font-style: italic; font-size: 0.8rem">
                  {{ truncate(item.summary, 30) }}
                </span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <!-- 分类分布 -->
        <el-card shadow="never">
          <template #header>
            <span>📊 分类分布</span>
          </template>
          <div v-if="categoryDist.length === 0" class="empty-placeholder">
            <span class="time-meta">暂无数据</span>
          </div>
          <div v-else class="category-list">
            <div
              v-for="cd in categoryDist"
              :key="cd.name"
              class="category-row"
              @click="$router.push(`/notes?category=${cd.name}`)"
            >
              <span class="category-name">{{ categoryLabel(cd.name) }}</span>
              <el-progress
                :percentage="cd.pct"
                :stroke-width="8"
                :color="categoryColor(cd.name)"
                :show-text="false"
                style="flex: 1; margin: 0 12px"
              />
              <span class="category-count">{{ cd.count }}</span>
            </div>
          </div>
        </el-card>

        <!-- 标签云 -->
        <el-card shadow="never" style="margin-top: 16px">
          <template #header>
            <div class="card-header">
              <span>🏷️ 热门标签</span>
              <el-button v-if="tags.length > 0" text size="small" type="primary" @click="$router.push('/notes')">
                全部
              </el-button>
            </div>
          </template>
          <div v-if="tags.length === 0" class="empty-placeholder">
            <span class="time-meta">暂无标签</span>
          </div>
          <div v-else class="tag-cloud">
            <el-tag
              v-for="tag in tags.slice(0, 15)"
              :key="tag.id"
              :hit="true"
              class="cloud-tag"
              :style="{ fontSize: tagSize(tag.usageCount) }"
              @click="$router.push(`/search?q=${encodeURIComponent(tag.name)}`)"
            >
              {{ tag.name }}
              <span class="tag-count">{{ tag.usageCount }}</span>
            </el-tag>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { listKnowledgeItems, listTags } from '@/api'

const recentNotes = ref([])
const tags = ref([])
const loading = ref(true)

const stats = ref({ total: 0, tags: 0, categories: 0, today: 0 })

const categoryDist = computed(() => {
  const count = {}
  for (const n of recentNotes.value) {
    const cat = n.autoCategory
    if (cat) count[cat] = (count[cat] || 0) + 1
  }
  const max = Math.max(1, ...Object.values(count))
  return Object.entries(count)
    .map(([name, c]) => ({ name, count: c, pct: Math.round((c / max) * 100) }))
    .sort((a, b) => b.count - a.count)
})

async function loadData() {
  loading.value = true
  try {
    const [pageData, tagList] = await Promise.all([
      listKnowledgeItems({ page: 0, size: 10 }),
      listTags(),
    ])
    recentNotes.value = pageData.items || []
    const allItems = pageData.items || []
    stats.value.total = pageData.totalElements || 0
    tags.value = tagList || []
    stats.value.tags = tags.value.length

    // 分类数（从全部标签推断，或从当前页）
    const cats = new Set(allItems.map(n => n.autoCategory).filter(Boolean))
    stats.value.categories = cats.size

    // 今日新增
    const today = new Date().toDateString()
    stats.value.today = allItems.filter(n => new Date(n.createdAt).toDateString() === today).length
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

function contentTypeLabel(t) {
  const map = { TEXT: '文字', AUDIO: '语音', IMAGE: '图片' }
  return map[t] || t
}
function tagType(t) {
  const map = { TEXT: '', AUDIO: 'success', IMAGE: 'warning' }
  return map[t] || ''
}
function truncate(s, n) {
  return s?.length > n ? s.slice(0, n) + '…' : s
}
function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
function categoryLabel(c) {
  const map = { TECH: '技术', WORK: '工作', LIFE: '生活', IDEA: '灵感', READING: '阅读', REFERENCE: '参考' }
  return map[c] || c
}
function categoryColor(c) {
  const map = { TECH: '#409EFF', WORK: '#E6A23C', LIFE: '#67C23A', IDEA: '#F56C6C', READING: '#909399', REFERENCE: '#B37FEB' }
  return map[c] || '#909399'
}
function tagSize(count) {
  if (count >= 5) return '1rem'
  if (count >= 3) return '0.9rem'
  return '0.8rem'
}

onMounted(loadData)
</script>

<style scoped>
.welcome-card {
  margin-bottom: 0;
}
.welcome-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.welcome-content h2 {
  margin: 0 0 4px;
}
.welcome-desc {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 0.9rem;
}
.welcome-actions {
  display: flex;
  gap: 8px;
}

.quick-actions {
  margin: 20px 0;
}
.action-card {
  cursor: pointer;
  margin-bottom: 16px;
}
.action-inner {
  display: flex;
  align-items: center;
  gap: 12px;
}
.action-icon {
  font-size: 2rem;
  flex-shrink: 0;
}
.action-title {
  font-weight: 600;
  font-size: 1rem;
}
.action-desc {
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

/* 统计卡片 */
.stats-row {
  margin-bottom: 20px;
}
.stat-card {
  cursor: default;
}
.stat-card:last-child {
  cursor: pointer;
}
.stat-inner {
  display: flex;
  align-items: center;
  gap: 12px;
}
.stat-icon {
  font-size: 1.6rem;
  flex-shrink: 0;
}
.stat-value {
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--el-color-primary);
}
.stat-label {
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.recent-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-light);
  cursor: pointer;
}
.recent-item:last-child {
  border-bottom: none;
}
.recent-item:hover .recent-text {
  color: var(--el-color-primary);
}
.recent-content {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}
.recent-text {
  flex: 1;
  line-height: 1.5;
  transition: color 0.15s;
}
.recent-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 52px;
  flex-wrap: wrap;
}

.loading-placeholder,
.empty-placeholder {
  padding: 20px 0;
}

/* 分类分布 */
.category-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.category-row {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 4px 0;
  border-radius: 4px;
  transition: background 0.15s;
}
.category-row:hover {
  background: var(--el-fill-color-light);
}
.category-name {
  font-size: 0.85rem;
  font-weight: 500;
  min-width: 50px;
}
.category-count {
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
  min-width: 20px;
  text-align: right;
}

/* 标签云 */
.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.cloud-tag {
  cursor: pointer;
  transition: transform 0.15s;
}
.cloud-tag:hover {
  transform: scale(1.08);
}
.tag-count {
  font-size: 0.7rem;
  color: var(--el-text-color-secondary);
  margin-left: 2px;
}
</style>
