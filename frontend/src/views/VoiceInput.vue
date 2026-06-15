<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <span>🎙️ 语音录入</span>
        <span class="time-meta" style="margin-left: 12px">录音后自动转写、清洗、入库</span>
      </template>

      <!-- 录音按钮 -->
      <div style="text-align: center; padding: 24px 0">
        <el-button
          :class="['record-btn', { recording: isRecording }]"
          :type="isRecording ? 'danger' : 'primary'"
          @click="toggleRecording"
          :disabled="uploading"
        >
          {{ isRecording ? '⏹️' : '🎙️' }}
        </el-button>
        <div style="margin-top: 12px">
          <span class="time-meta">{{ statusText }}</span>
        </div>
        <div v-if="isRecording" class="timer">{{ formattedTime }}</div>
        <div style="margin-top: 8px; font-size: 0.8rem; color: var(--el-text-color-secondary)">
          支持 WebM / WAV / MP3 · 最长 5 分钟
        </div>
      </div>

      <!-- 加载 -->
      <div v-if="uploading" style="text-align: center; padding: 20px">
        <el-progress type="circle" :percentage="80" :stroke-width="6" status="success" />
        <p style="margin-top: 12px; color: var(--el-text-color-secondary)">正在转写中，请稍候…</p>
      </div>

      <!-- 结果 -->
      <div v-if="result">
        <el-card shadow="never" class="result-card">
          <template #header>
            <span class="result-label">📝 ASR 转写</span>
          </template>
          <div class="result-text">{{ result.asrText }}</div>
        </el-card>

        <el-card shadow="never" class="result-card">
          <template #header>
            <span class="result-label">✨ 清洗后文本</span>
          </template>
          <div class="result-text">{{ result.cleanedText }}</div>
        </el-card>

        <el-card shadow="never" class="result-card note-card">
          <div style="display: flex; justify-content: space-between; align-items: center">
            <div>
              <span class="result-label">📌 笔记已入库</span>
              <div class="time-meta">自动双写索引（pgvector + Lucene BM25）</div>
            </div>
            <el-tag>{{ result.knowledgeItem?.id ? result.knowledgeItem.id.slice(0, 8) + '…' : '' }}</el-tag>
          </div>
        </el-card>

        <el-button style="margin-top: 12px" @click="resetForm">🔄 再录一条</el-button>
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
import { ref, computed } from 'vue'
import { transcribeVoice } from '@/api'
import { ElMessage } from 'element-plus'

const isRecording = ref(false)
const uploading = ref(false)
const result = ref(null)
const statusText = ref('点击按钮开始录音')
const seconds = ref(0)
let mediaRecorder = null
let audioChunks = []
let timerInterval = null

const formattedTime = computed(() => {
  const m = String(Math.floor(seconds.value / 60)).padStart(2, '0')
  const s = String(seconds.value % 60).padStart(2, '0')
  return `${m}:${s}`
})

async function toggleRecording() {
  if (isRecording.value) {
    stopRecording()
  } else {
    await startRecording()
  }
}

async function startRecording() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    audioChunks = []
    seconds.value = 0

    const mimeType = chooseMimeType()
    mediaRecorder = new MediaRecorder(stream, { mimeType })

    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) audioChunks.push(e.data)
    }

    mediaRecorder.onstop = () => {
      stream.getTracks().forEach((t) => t.stop())
      clearInterval(timerInterval)
      uploadAudio()
    }

    mediaRecorder.start(100)
    isRecording.value = true
    statusText.value = '录音中…'
    timerInterval = setInterval(() => { seconds.value++ }, 1000)

    // 最长 5 分钟自动停止
    setTimeout(() => {
      if (isRecording.value) stopRecording()
    }, 300000)
  } catch (e) {
    ElMessage.error('无法访问麦克风：' + e.message)
  }
}

function stopRecording() {
  if (!mediaRecorder || mediaRecorder.state === 'inactive') return
  mediaRecorder.stop()
  isRecording.value = false
  statusText.value = '上传中…'
}

function chooseMimeType() {
  const types = [
    'audio/webm;codecs=opus',
    'audio/webm',
    'audio/mp4',
    'audio/ogg;codecs=opus',
    'audio/wav',
  ]
  for (const t of types) {
    if (MediaRecorder.isTypeSupported(t)) return t
  }
  return 'audio/webm'
}

async function uploadAudio() {
  uploading.value = true
  statusText.value = '转写中…'
  try {
    const blob = new Blob(audioChunks, { type: mediaRecorder.mimeType })
    const ext = mediaRecorder.mimeType.split('/')[1]?.split(';')[0] || 'webm'
    const file = new File([blob], `recording.${ext}`, { type: mediaRecorder.mimeType })

    const data = await transcribeVoice(file)
    result.value = data
    statusText.value = '✅ 转写完成，笔记已入库'
  } catch (e) {
    statusText.value = '❌ 转写失败，请重试'
    ElMessage.error(e.message)
  } finally {
    uploading.value = false
    audioChunks = []
  }
}

function resetForm() {
  result.value = null
  statusText.value = '点击按钮开始录音'
}
</script>

<style scoped>
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
.timer {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--el-color-danger);
  margin-top: 8px;
  font-variant-numeric: tabular-nums;
}
</style>
