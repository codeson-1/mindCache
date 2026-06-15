import axios from 'axios'

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

// 响应拦截：统一处理错误
client.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body.code !== 200) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body.data
  },
  (err) => {
    const msg = err.response?.data?.message || err.message || '网络错误'
    return Promise.reject(new Error(msg))
  },
)

// ===== 知识条目 =====

/** 统一录入 */
export function createKnowledgeItem(data) {
  return client.post('/knowledge-items', data)
}

/** 分页列表 */
export function listKnowledgeItems(params) {
  return client.get('/knowledge-items', { params })
}

/** 详情 */
export function getKnowledgeItem(id) {
  return client.get(`/knowledge-items/${id}`)
}

/** 更新 */
export function updateKnowledgeItem(id, data) {
  return client.put(`/knowledge-items/${id}`, data)
}

/** 删除 */
export function deleteKnowledgeItem(id) {
  return client.delete(`/knowledge-items/${id}`)
}

/** 相关笔记 */
export function getRelatedItems(id, topK = 5) {
  return client.get(`/knowledge-items/${id}/related`, { params: { topK } })
}

// ===== 搜索 =====

/** 多路融合检索（POST 推荐） */
export function searchItems(params) {
  return client.post('/search', params)
}

// ===== 分类与标签 =====

/** 用户修正分类 */
export function correctClassification(id, data) {
  return client.put(`/knowledge-items/${id}/classification`, data)
}

/** 标签列表 */
export function listTags() {
  return client.get('/tags')
}

// ===== 语音 =====

/** 上传语音文件并转写 */
export function transcribeVoice(file) {
  const form = new FormData()
  form.append('file', file)
  return client.post('/voice/transcribe', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000, // 语音转写可能较慢
  })
}

// ===== 图片 =====

/** 上传图片并分析 */
export function uploadImage(file) {
  const form = new FormData()
  form.append('file', file)
  return client.post('/images/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
}

// ===== 每日摘要 =====

/** 获取每日摘要 SSE 流地址 */
export function getDailySummaryUrl() {
  return '/api/v1/summaries/daily'
}

export default client
