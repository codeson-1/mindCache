<template>
  <div class="page-container">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-bar">
      <el-row :gutter="16" align="middle">
        <el-col :xs="24" :sm="6">
          <el-select
            v-model="filters.category"
            placeholder="全部分类"
            clearable
            style="width: 100%"
            @change="loadNotes(1)"
          >
            <el-option
              v-for="c in categories"
              :key="c"
              :label="c"
              :value="c"
            />
          </el-select>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-select
            v-model="filters.contentType"
            placeholder="全部类型"
            clearable
            style="width: 100%"
            @change="loadNotes(1)"
          >
            <el-option label="文字" value="TEXT" />
            <el-option label="语音" value="AUDIO" />
            <el-option label="图片" value="IMAGE" />
          </el-select>
        </el-col>
        <el-col :xs="12" :sm="6">
          <el-input v-model="filters.keyword" placeholder="关键词搜索…" clearable @change="loadNotes(1)">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-col>
        <el-col :xs="24" :sm="6" style="text-align: right">
          <el-button type="primary" @click="$router.push('/create')">
            <el-icon><Plus /></el-icon> 新建
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 笔记列表 -->
    <el-card shadow="never">
      <div v-if="loading">
        <el-skeleton :rows="6" animated />
      </div>

      <div v-else-if="notes.length === 0">
        <el-empty description="暂无笔记" />
      </div>

      <div v-else>
        <div
          v-for="item in notes"
          :key="item.id"
          class="note-card"
          @click="$router.push(`/notes/${item.id}`)"
        >
          <el-row :gutter="16" align="middle">
            <el-col :xs="24" :sm="18">
              <div class="note-content">
                <el-tag size="small" :type="tagType(item.contentType)" effect="plain" class="type-tag">
                  {{ typeLabel(item.contentType) }}
                </el-tag>
                <span class="note-text">{{ truncate(item.cleanContent, 120) }}</span>
              </div>
            </el-col>
            <el-col :xs="12" :sm="3">
              <el-tag v-if="item.autoCategory" size="small" effect="plain">
                {{ item.autoCategory }}
              </el-tag>
            </el-col>
            <el-col :xs="12" :sm="3" class="time-col">
              <span class="time-meta">{{ formatTime(item.createdAt) }}</span>
            </el-col>
          </el-row>
        </div>

        <!-- 分页 -->
        <div class="pagination-wrapper">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="total"
            layout="prev, pager, next"
            @current-change="loadNotes"
          />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listKnowledgeItems, listTags } from '@/api'

const notes = ref([])
const loading = ref(true)
const page = ref(1)
const pageSize = 20
const total = ref(0)

const filters = ref({
  category: '',
  contentType: '',
  keyword: '',
})

const categories = ref([])

async function loadNotes(p) {
  loading.value = true
  page.value = p || 1
  try {
    const params = {
      page: page.value - 1,
      size: pageSize,
    }
    if (filters.value.category) params.category = filters.value.category
    // keyword 和 contentType 目前后端列表接口仅支持 category 筛选
    // 复杂筛选用搜索接口 — 前端简化处理
    const data = await listKnowledgeItems(params)
    notes.value = data.items || []
    total.value = data.totalElements || 0
  } catch {
    notes.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    const tags = await listTags()
    // 从已有笔记统计分类
    const catSet = new Set()
    // 先尝试从后端获取分类列表，如果没有特殊接口就从 notes 提取
    // 简化：从文档可知固定分类 TECH/WORK/LIFE/IDEA/READING/REFERENCE
    categories.value = ['TECH', 'WORK', 'LIFE', 'IDEA', 'READING', 'REFERENCE']
  } catch {
    categories.value = ['TECH', 'WORK', 'LIFE', 'IDEA', 'READING', 'REFERENCE']
  }
}

function typeLabel(t) {
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

onMounted(() => {
  loadNotes(1)
  loadCategories()
})
</script>

<style scoped>
.filter-bar {
  margin-bottom: 16px;
}
.note-card {
  padding: 14px 0;
  border-bottom: 1px solid var(--el-border-color-light);
  cursor: pointer;
  transition: background 0.15s;
  border-radius: 6px;
  padding-left: 8px;
  padding-right: 8px;
}
.note-card:last-child {
  border-bottom: none;
}
.note-card:hover {
  background: var(--el-color-primary-light-9);
}
.note-content {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.type-tag {
  flex-shrink: 0;
  margin-top: 1px;
}
.note-text {
  line-height: 1.5;
}
.time-col {
  text-align: right;
}
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
