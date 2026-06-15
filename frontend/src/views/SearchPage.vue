<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div style="display: flex; align-items: center; gap: 12px">
          <span>🔍 语义搜索</span>
          <span class="time-meta">三路融合检索：BM25 关键词 + 向量语义 + 时间衰减</span>
        </div>
      </template>

      <!-- 搜索框 -->
      <el-row :gutter="12">
        <el-col :xs="24" :sm="16">
          <el-input
            ref="searchInputRef"
            v-model="query"
            size="large"
            placeholder="搜索你的知识碎片，例如「上周那个关于微服务的架构图」…"
            clearable
            @keyup.enter="doSearch"
            @clear="clearSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-col>
        <el-col :xs="12" :sm="3">
          <el-select v-model="category" placeholder="分类" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c" :label="categoryLabel(c)" :value="c" />
          </el-select>
        </el-col>
        <el-col :xs="12" :sm="3">
          <el-select v-model="contentType" placeholder="类型" clearable style="width: 100%">
            <el-option label="文字" value="TEXT" />
            <el-option label="语音" value="AUDIO" />
            <el-option label="图片" value="IMAGE" />
          </el-select>
        </el-col>
        <el-col :xs="24" :sm="2">
          <el-button type="primary" size="large" style="width: 100%" @click="doSearch" :loading="loading">
            搜索
          </el-button>
        </el-col>
      </el-row>

      <!-- 快捷搜索提示 -->
      <div v-if="recentSearches.length > 0 && !searched" class="search-hints">
        <span class="hint-label">最近搜索：</span>
        <el-tag
          v-for="(s, idx) in recentSearches"
          :key="idx"
          class="hint-tag"
          @click="quickSearch(s)"
        >
          {{ s }}
        </el-tag>
      </div>
    </el-card>

    <!-- 搜索结果 -->
    <el-card shadow="never" style="margin-top: 16px">
      <div v-if="!searched" class="empty-hint">
        <el-empty description="输入关键词开始搜索" :image-size="120">
          <template #default>
            <div class="suggested-queries">
              <div class="suggested-label">试试搜索：</div>
              <el-button
                v-for="sq in suggestedQueries"
                :key="sq"
                text
                type="primary"
                size="small"
                @click="quickSearch(sq)"
              >
                {{ sq }}
              </el-button>
            </div>
          </template>
        </el-empty>
      </div>

      <div v-else-if="loading">
        <el-skeleton :rows="6" animated />
      </div>

      <div v-else-if="results.length === 0">
        <el-empty description="未找到匹配结果">
          <template #default>
            <div style="color: var(--el-text-color-secondary); font-size: 0.9rem">
              试试更换搜索词或调整筛选条件
            </div>
          </template>
        </el-empty>
      </div>

      <div v-else>
        <div class="result-count">
          共找到 <strong>{{ results.length }}</strong> 条结果
          <span class="time-meta" style="margin-left: 8px">
            · 融合评分 = {{ alpha }}×语义 + {{ beta }}×关键词 + {{ gamma }}×时间
          </span>
        </div>

        <div
          v-for="(r, idx) in results"
          :key="r.item.id"
          class="search-result-card"
          @click="$router.push(`/notes/${r.item.id}`)"
        >
          <el-row :gutter="16">
            <el-col :xs="24" :sm="18">
              <div class="result-header">
                <el-tag size="small" :type="tagType(r.item.contentType)" effect="plain">
                  {{ typeLabel(r.item.contentType) }}
                </el-tag>
                <el-tag v-if="r.item.autoCategory" size="small" effect="plain">{{ r.item.autoCategory }}</el-tag>
                <span v-if="r.item.summary" class="result-summary time-meta">{{ r.item.summary }}</span>
              </div>
              <div class="result-content">{{ truncate(r.item.cleanContent, 200) }}</div>
            </el-col>
            <el-col :xs="24" :sm="6" class="result-meta-col">
              <div class="time-meta">{{ formatTime(r.item.createdAt) }}</div>

              <!-- 评分进度条 + 明细 popover -->
              <el-popover placement="left" :width="280" trigger="hover">
                <template #reference>
                  <div class="score-bar">
                    <el-progress
                      :percentage="Math.round((r.fusedScore || 0) * 100)"
                      :stroke-width="8"
                      :color="scoreColor(r.fusedScore)"
                    />
                  </div>
                </template>
                <div class="score-breakdown">
                  <div class="breakdown-title">🔬 评分明细</div>
                  <div class="breakdown-row">
                    <span>向量语义</span>
                    <span class="breakdown-val">{{ (r.vectorScore * 100).toFixed(1) }}%</span>
                    <span class="breakdown-w">×{{ alpha }}</span>
                  </div>
                  <div class="breakdown-row">
                    <span>BM25 关键词</span>
                    <span class="breakdown-val">{{ (r.bm25Score * 100).toFixed(1) }}%</span>
                    <span class="breakdown-w">×{{ beta }}</span>
                  </div>
                  <div class="breakdown-row">
                    <span>时间衰减</span>
                    <span class="breakdown-val">{{ (r.timeDecay * 100).toFixed(1) }}%</span>
                    <span class="breakdown-w">×{{ gamma }}</span>
                  </div>
                  <el-divider style="margin: 8px 0" />
                  <div class="breakdown-row total">
                    <span>融合评分</span>
                    <span class="breakdown-val">{{ (r.fusedScore * 100).toFixed(1) }}%</span>
                  </div>
                </div>
              </el-popover>
            </el-col>
          </el-row>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { searchItems } from '@/api'

const query = ref('')
const category = ref('')
const contentType = ref('')
const results = ref([])
const loading = ref(false)
const searched = ref(false)
const searchInputRef = ref(null)

const alpha = 0.3
const beta = 0.5
const gamma = 0.2

const categories = ['TECH', 'WORK', 'LIFE', 'IDEA', 'READING', 'REFERENCE']
const suggestedQueries = ['Spring Cloud', '微服务架构', 'Docker', '数据库', 'AI']

// 最近搜索（localStorage）
const recentSearches = ref(loadRecentSearches())

function loadRecentSearches() {
  try {
    return JSON.parse(localStorage.getItem('mc_recent_searches') || '[]')
  } catch { return [] }
}

function saveRecentSearch(q) {
  let list = loadRecentSearches()
  list = list.filter(s => s !== q)
  list.unshift(q)
  if (list.length > 6) list.pop()
  localStorage.setItem('mc_recent_searches', JSON.stringify(list))
  recentSearches.value = list
}

async function doSearch() {
  const q = query.value.trim()
  if (!q) return
  loading.value = true
  searched.value = true
  results.value = []
  saveRecentSearch(q)
  try {
    const params = { query: q, topK: 20 }
    if (category.value) params.category = category.value
    if (contentType.value) params.contentType = contentType.value
    const data = await searchItems(params)
    results.value = data?.results || []
  } catch {
    results.value = []
  } finally {
    loading.value = false
  }
}

function quickSearch(q) {
  query.value = q
  nextTick(() => doSearch())
}

function clearSearch() {
  results.value = []
  searched.value = false
}

function categoryLabel(c) {
  const map = { TECH: '技术', WORK: '工作', LIFE: '生活', IDEA: '灵感', READING: '阅读', REFERENCE: '参考' }
  return map[c] || c
}

function typeLabel(t) {
  const map = { TEXT: '文字', AUDIO: '语音', IMAGE: '图片' }
  return map[t] || t
}

function tagType(t) {
  const map = { TEXT: '', AUDIO: 'success', IMAGE: 'warning' }
  return map[t] || ''
}

function scoreColor(score) {
  if (!score) return '#909399'
  if (score > 0.7) return '#67C23A'
  if (score > 0.4) return '#E6A23C'
  return '#F56C6C'
}

function truncate(s, n) {
  return s?.length > n ? s.slice(0, n) + '…' : s
}

function formatTime(ts) {
  if (!ts) return ''
  return new Date(ts).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  // Auto-focus search input
  nextTick(() => {
    searchInputRef.value?.focus?.()
  })
})
</script>

<style scoped>
.result-count {
  font-size: 0.9rem;
  color: var(--el-text-color-secondary);
  margin-bottom: 16px;
}

/* 快捷搜索提示 */
.search-hints {
  margin-top: 12px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.hint-label {
  font-size: 0.8rem;
  color: var(--el-text-color-secondary);
}
.hint-tag {
  cursor: pointer;
  transition: all 0.15s;
}
.hint-tag:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.suggested-queries {
  padding-top: 8px;
}
.suggested-label {
  font-size: 0.85rem;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.search-result-card {
  padding: 16px 0;
  border-bottom: 1px solid var(--el-border-color-light);
  cursor: pointer;
  transition: background 0.15s, transform 0.1s;
  border-radius: 8px;
  padding-left: 8px;
  padding-right: 8px;
}
.search-result-card:last-child {
  border-bottom: none;
}
.search-result-card:hover {
  background: var(--el-color-primary-light-9);
  transform: translateX(2px);
}

.result-header {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.result-summary {
  font-style: italic;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.result-content {
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.result-meta-col {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  text-align: right;
}

.empty-hint {
  padding: 10px 0;
}

.score-bar {
  min-width: 80px;
  cursor: pointer;
}

/* 评分明细 popover */
.score-breakdown {
  font-size: 0.85rem;
}
.breakdown-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.breakdown-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
  gap: 8px;
}
.breakdown-row.total {
  font-weight: 600;
  color: var(--el-color-primary);
  font-size: 0.9rem;
}
.breakdown-val {
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}
.breakdown-w {
  color: var(--el-text-color-secondary);
  font-size: 0.8rem;
  min-width: 30px;
  text-align: right;
}
</style>
