<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span>✍️ 文字录入</span>
      </template>

      <el-form label-position="top">
        <el-form-item label="笔记内容">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="输入你想记录的知识碎片…"
            maxlength="10000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="来源（可选）">
          <el-select v-model="form.sourceType" placeholder="选择来源" clearable style="width: 200px">
            <el-option label="手动录入" value="MANUAL" />
            <el-option label="网页剪藏" value="WEB_CLIP" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" :loading="submitting" @click="handleSubmit">
            <el-icon><Upload /></el-icon> 提交并入库
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 结果展示 -->
      <el-card v-if="result" shadow="never" class="result-card">
        <template #header>
          <el-tag type="success">✅ 笔记已入库</el-tag>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="ID">
            <code>{{ result.id }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="分类">
            {{ result.autoCategory || '待分类' }}
          </el-descriptions-item>
          <el-descriptions-item label="标签">
            <el-tag
              v-for="tag in (result.tags || [])"
              :key="tag.id || tag"
              size="small"
              style="margin-right: 4px"
            >
              {{ tag.name || tag }}
            </el-tag>
            <span v-if="!result.tags?.length" class="time-meta">AI 自动生成中…</span>
          </el-descriptions-item>
          <el-descriptions-item label="摘要">
            {{ result.summary || 'AI 自动生成中…' }}
          </el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 12px">
          <el-button @click="resetForm">再记一条</el-button>
          <el-button text @click="$router.push(`/notes/${result.id}`)">查看详情</el-button>
        </div>
      </el-card>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { createKnowledgeItem } from '@/api'
import { ElMessage } from 'element-plus'

const form = reactive({
  content: '',
  sourceType: 'MANUAL',
})
const submitting = ref(false)
const result = ref(null)

async function handleSubmit() {
  if (!form.content.trim()) {
    ElMessage.warning('请输入笔记内容')
    return
  }
  submitting.value = true
  result.value = null
  try {
    const data = await createKnowledgeItem({
      rawContent: form.content,
      contentType: 'TEXT',
      sourceType: form.sourceType || 'MANUAL',
    })
    result.value = data
    ElMessage.success('笔记已入库 ✓')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.content = ''
  form.sourceType = 'MANUAL'
  result.value = null
}
</script>

<style scoped>
.result-card {
  margin-top: 20px;
  border: 1px solid var(--el-color-success-light-5);
}
</style>
