<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span>🖼️ 图片录入</span>
        <span class="time-meta" style="margin-left: 12px">上传图片 → OCR 文字提取 + AI 视觉描述 → 自动入库</span>
      </template>

      <!-- 上传区域 -->
      <div
        :class="['upload-zone', { dragover: isDragover }]"
        @click="fileInput?.click()"
        @dragover.prevent="isDragover = true"
        @dragleave="isDragover = false"
        @drop.prevent="handleDrop"
      >
        <div style="font-size: 3rem; margin-bottom: 8px">📷</div>
        <div class="time-meta">点击选择图片，或拖拽到此处</div>
        <div class="time-meta" style="margin-top: 4px; font-size: 0.8rem">
          支持 PNG / JPG / GIF / WebP · 最大 25MB
        </div>
        <input
          ref="fileInput"
          type="file"
          accept="image/*"
          style="display: none"
          @change="onFileSelected"
        />
      </div>

      <!-- 预览 -->
      <div v-if="previewUrl" class="preview-section">
        <el-card shadow="never">
          <div style="text-align: center">
            <img :src="previewUrl" class="preview-img" alt="预览" />
          </div>
          <div class="preview-actions">
            <el-button type="primary" :loading="analyzing" @click="handleUpload">
              🔍 分析图片
            </el-button>
            <el-button @click="clearPreview">换一张</el-button>
          </div>
        </el-card>
      </div>

      <!-- 加载 -->
      <div v-if="analyzing" style="text-align: center; padding: 20px">
        <el-progress type="circle" :percentage="80" :stroke-width="6" status="success" />
        <p style="margin-top: 12px; color: var(--el-text-color-secondary)">正在分析图片，请稍候…</p>
      </div>

      <!-- 结果 -->
      <div v-if="result">
        <el-card shadow="never" class="result-card">
          <template #header>
            <span class="result-label">📝 OCR 文字提取</span>
          </template>
          <div class="result-text">{{ result.ocrText || '（无文字）' }}</div>
        </el-card>

        <el-card shadow="never" class="result-card">
          <template #header>
            <span class="result-label">👁️ AI 视觉描述</span>
          </template>
          <div class="result-text">{{ result.visualDescription }}</div>
        </el-card>

        <el-card shadow="never" class="result-card">
          <template #header>
            <span class="result-label">📌 入库文本（融合后）</span>
          </template>
          <div class="result-text merged">{{ result.mergedContent }}</div>
        </el-card>

        <el-card shadow="never" class="result-card note-card">
          <div style="display: flex; justify-content: space-between; align-items: center">
            <div>
              <span class="result-label">✅ 笔记已入库</span>
              <div class="time-meta">自动双写索引（pgvector + Lucene BM25）</div>
            </div>
            <el-tag>{{ result.knowledgeItem?.id ? result.knowledgeItem.id.slice(0, 8) + '…' : '' }}</el-tag>
          </div>
        </el-card>

        <el-button style="margin-top: 12px" @click="clearAll">📷 再传一张</el-button>
        <el-button
          v-if="result.knowledgeItem?.id"
          text
          @click="$router.push(`/notes/${result.knowledgeItem.id}`)"
        >
          查看详情
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { uploadImage } from '@/api'
import { ElMessage } from 'element-plus'

const fileInput = ref(null)
const isDragover = ref(false)
const previewUrl = ref('')
const analyzing = ref(false)
const result = ref(null)
let selectedFile = null

function onFileSelected(e) {
  const file = e.target.files[0]
  if (file) showPreview(file)
}

function handleDrop(e) {
  isDragover.value = false
  const file = e.dataTransfer.files[0]
  if (file && file.type.startsWith('image/')) {
    showPreview(file)
  } else {
    ElMessage.warning('请上传图片文件')
  }
}

function showPreview(file) {
  selectedFile = file
  const reader = new FileReader()
  reader.onload = (e) => {
    previewUrl.value = e.target.result
  }
  reader.readAsDataURL(file)
}

function clearPreview() {
  previewUrl.value = ''
  selectedFile = null
  if (fileInput.value) fileInput.value.value = ''
}

async function handleUpload() {
  if (!selectedFile) return
  analyzing.value = true
  result.value = null
  try {
    const data = await uploadImage(selectedFile)
    result.value = data
    ElMessage.success('图片分析完成 ✅')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    analyzing.value = false
  }
}

function clearAll() {
  result.value = null
  clearPreview()
}
</script>

<style scoped>
.preview-section {
  margin-top: 16px;
}
.preview-img {
  max-width: 100%;
  max-height: 360px;
  border-radius: 8px;
  box-shadow: var(--el-box-shadow-light);
}
.preview-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 12px;
}
.result-card {
  margin-bottom: 12px;
}
.result-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.result-text {
  background: var(--el-fill-color-light);
  border-radius: 8px;
  padding: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}
.result-text.merged {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
}
</style>
