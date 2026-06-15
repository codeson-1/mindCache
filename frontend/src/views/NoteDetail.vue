<template>
  <div class="page-container">
    <el-button text @click="$router.push('/notes')">
      <el-icon><ArrowLeft /></el-icon> 返回列表
    </el-button>

    <div v-if="loading">
      <el-skeleton :rows="8" animated style="margin-top: 16px" />
    </div>

    <template v-else-if="item">
      <el-card shadow="never" class="detail-card">
        <template #header>
          <div class="detail-header">
            <div class="header-left">
              <el-tag :type="typeTag" effect="plain" size="small">
                {{ typeLabel }}
              </el-tag>

              <!-- 分类：点击可修改 -->
              <el-popover
                v-if="!editingCategory"
                placement="bottom"
                :width="260"
                trigger="click"
              >
                <template #reference>
                  <el-tag
                    size="small"
                    :type="categoryTagType"
                    effect="plain"
                    style="cursor: pointer"
                  >
                    {{ displayCategory || '未分类' }}
                    <el-icon style="margin-left: 2px"><EditPen /></el-icon>
                  </el-tag>
                </template>
                <div class="category-edit-pop">
                  <div class="pop-title">修正分类</div>
                  <el-radio-group v-model="newCategory" class="category-radios">
                    <el-radio
                      v-for="c in categories"
                      :key="c"
                      :value="c"
                      size="small"
                    >
                      {{ categoryLabel(c) }}
                    </el-radio>
                  </el-radio-group>
                  <div style="margin-top: 12px; display: flex; gap: 8px">
                    <el-button size="small" @click="newCategory = null">清空</el-button>
                    <el-button size="small" type="primary" :loading="savingCategory" @click="saveCategory">
                      确认
                    </el-button>
                  </div>
                </div>
              </el-popover>

              <span v-if="item.userCategory && item.userCategory !== item.autoCategory" class="time-meta" style="font-size: 0.75rem">
                AI 原分类: {{ item.autoCategory }}
              </span>
            </div>
            <div class="header-actions">
              <el-button size="small" @click="editMode = !editMode">
                <el-icon><Edit /></el-icon> {{ editMode ? '取消编辑' : '编辑内容' }}
              </el-button>
              <el-popconfirm title="确认删除这条笔记？" @confirm="handleDelete">
                <template #reference>
                  <el-button size="small" type="danger">
                    <el-icon><Delete /></el-icon> 删除
                  </el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>
        </template>

        <!-- 查看模式 -->
        <div v-if="!editMode">
          <div class="content-block">
            <div class="section-label">📝 内容</div>
            <div class="content-text">{{ item.cleanContent }}</div>
            <div v-if="item.rawContent !== item.cleanContent" class="raw-content">
              <div class="section-label">📋 原始内容</div>
              <div class="content-text muted">{{ item.rawContent }}</div>
            </div>
          </div>

          <el-descriptions :column="2" border style="margin-top: 20px">
            <el-descriptions-item label="摘要" :span="2">
              {{ item.summary || '无' }}
            </el-descriptions-item>
            <el-descriptions-item label="来源">
              {{ sourceLabel(item.sourceType) }}
            </el-descriptions-item>
            <el-descriptions-item label="录入时间">
              {{ formatTime(item.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="标签" :span="2">
              <div class="tag-area">
                <el-tag
                  v-for="(tag, idx) in (item.tags || [])"
                  :key="tag.id || tag"
                  size="small"
                  closable
                  class="item-tag"
                  @close="handleRemoveTag(idx)"
                  @click.stop="searchByTag(tag.name || tag)"
                >
                  {{ tag.name || tag }}
                </el-tag>
                <el-input
                  v-if="addingTag"
                  ref="tagInputRef"
                  v-model="newTagName"
                  size="small"
                  style="width: 100px"
                  placeholder="新标签"
                  @keyup.enter="handleAddTag"
                  @blur="handleAddTag"
                  @keyup.escape="addingTag = false; newTagName = ''"
                />
                <el-button
                  v-else
                  size="small"
                  :icon="Plus"
                  circle
                  @click="startAddTag"
                />
                <span v-if="!item.tags?.length && !addingTag" class="time-meta">点击 + 添加标签</span>
              </div>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 编辑模式 -->
        <div v-else>
          <el-form label-position="top">
            <el-form-item label="内容">
              <el-input
                v-model="editForm.content"
                type="textarea"
                :rows="6"
                placeholder="编辑内容…"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="handleUpdate">
                保存
              </el-button>
              <el-button @click="editMode = false">取消</el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-card>

      <!-- 相关笔记 -->
      <el-card shadow="never" class="related-card" v-if="relatedItems.length > 0">
        <template #header>
          <span>🔗 相关笔记</span>
        </template>
        <div
          v-for="r in relatedItems"
          :key="r.item.id"
          class="related-item"
          @click="$router.push(`/notes/${r.item.id}`)"
        >
          <div class="related-text">{{ truncate(r.item.cleanContent, 80) }}</div>
          <div class="related-meta-right">
            <span v-if="r.item.autoCategory" class="time-meta" style="margin-right: 8px">{{ r.item.autoCategory }}</span>
            <span class="related-score">相关度 {{ (r.fusedScore * 100).toFixed(0) }}%</span>
          </div>
        </div>
      </el-card>
    </template>

    <div v-else-if="!loading">
      <el-empty description="笔记不存在" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getKnowledgeItem,
  updateKnowledgeItem,
  deleteKnowledgeItem,
  getRelatedItems,
  correctClassification,
} from '@/api'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const item = ref(null)
const relatedItems = ref([])
const loading = ref(true)
const editMode = ref(false)
const saving = ref(false)

const categories = ['TECH', 'WORK', 'LIFE', 'IDEA', 'READING', 'REFERENCE']

const editForm = ref({ content: '', category: '' })

// 分类 inline 编辑
const newCategory = ref(null)
const savingCategory = ref(false)

// 标签 inline 编辑
const addingTag = ref(false)
const newTagName = ref('')
const tagInputRef = ref(null)

const displayCategory = computed(() => {
  if (!item.value) return ''
  return item.value.userCategory || item.value.autoCategory || ''
})

const categoryTagType = computed(() => {
  if (item.value?.userCategory) return 'warning'
  return ''
})

const typeLabel = computed(() => {
  const map = { TEXT: '文字', AUDIO: '语音', IMAGE: '图片' }
  return map[item.value?.contentType] || ''
})
const typeTag = computed(() => {
  const map = { TEXT: '', AUDIO: 'success', IMAGE: 'warning' }
  return map[item.value?.contentType] || ''
})

function categoryLabel(c) {
  const map = { TECH: '技术', WORK: '工作', LIFE: '生活', IDEA: '灵感', READING: '阅读', REFERENCE: '参考' }
  return `${map[c] || c} (${c})`
}

async function loadDetail() {
  loading.value = true
  try {
    const data = await getKnowledgeItem(route.params.id)
    item.value = data
    editForm.value.content = data.cleanContent
    newCategory.value = data.userCategory || data.autoCategory || null
  } catch {
    item.value = null
  } finally {
    loading.value = false
  }
}

async function loadRelated() {
  try {
    const data = await getRelatedItems(route.params.id, 5)
    relatedItems.value = data?.related || []
  } catch {
    relatedItems.value = []
  }
}

// 分类编辑
async function saveCategory() {
  savingCategory.value = true
  try {
    const currentTags = (item.value.tags || []).map(t => t.name || t)
    const updated = await correctClassification(route.params.id, {
      correctedCategory: newCategory.value || null,
      correctedTags: currentTags,
    })
    item.value = updated
    ElMessage.success('分类已更新')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    savingCategory.value = false
  }
}

// 标签编辑
function startAddTag() {
  addingTag.value = true
  nextTick(() => tagInputRef.value?.focus?.())
}

async function handleAddTag() {
  const name = newTagName.value.trim()
  addingTag.value = false
  newTagName.value = ''
  if (!name) return

  const currentTags = (item.value.tags || []).map(t => t.name || t)
  if (currentTags.includes(name)) {
    ElMessage.warning('标签已存在')
    return
  }
  currentTags.push(name)
  await syncTags(currentTags)
}

async function handleRemoveTag(idx) {
  const currentTags = (item.value.tags || []).map(t => t.name || t)
  currentTags.splice(idx, 1)
  await syncTags(currentTags)
}

async function syncTags(tagNames) {
  try {
    const updated = await correctClassification(route.params.id, {
      correctedCategory: null,  // 不改变分类
      correctedTags: tagNames,
    })
    item.value = updated
    ElMessage.success('标签已更新')
  } catch (e) {
    ElMessage.error(e.message)
    loadDetail()  // 恢复
  }
}

function searchByTag(tag) {
  router.push({ path: '/search', query: { q: tag } })
}

async function handleUpdate() {
  saving.value = true
  try {
    const updated = await updateKnowledgeItem(route.params.id, {
      rawContent: editForm.value.content,
    })
    item.value = updated
    editMode.value = false
    ElMessage.success('已更新')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  try {
    await deleteKnowledgeItem(route.params.id)
    ElMessage.success('已删除')
    router.push('/notes')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function sourceLabel(s) {
  const map = { MANUAL: '手动录入', VOICE: '语音转写', UPLOAD: '图片上传', WEB_CLIP: '网页剪藏' }
  return map[s] || s || '未知'
}
function formatTime(ts) {
  if (!ts) return ''
  return new Date(ts).toLocaleString('zh-CN')
}
function truncate(s, n) {
  return s?.length > n ? s.slice(0, n) + '…' : s
}

onMounted(() => {
  loadDetail()
  loadRelated()
})
</script>

<style scoped>
.detail-card {
  margin-top: 12px;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.header-actions {
  display: flex;
  gap: 8px;
}

.content-block {
  margin-bottom: 8px;
}
.section-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.content-text {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 16px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.content-text.muted {
  color: var(--el-text-color-secondary);
  font-size: 0.9rem;
}
.raw-content {
  margin-top: 16px;
}

/* 分类编辑 popover */
.category-edit-pop {
  padding: 4px 0;
}
.pop-title {
  font-weight: 600;
  margin-bottom: 8px;
  font-size: 0.9rem;
}
.category-radios {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

/* 标签区域 */
.tag-area {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.item-tag {
  cursor: pointer;
  transition: transform 0.1s;
}
.item-tag:hover {
  transform: scale(1.05);
}

.related-card {
  margin-top: 16px;
}
.related-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-light);
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.related-item:last-child {
  border-bottom: none;
}
.related-item:hover .related-text {
  color: var(--el-color-primary);
}
.related-text {
  flex: 1;
  line-height: 1.5;
  min-width: 0;
}
.related-meta-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  font-size: 0.8rem;
}
.related-score {
  color: var(--el-color-primary);
  font-weight: 500;
}
</style>
