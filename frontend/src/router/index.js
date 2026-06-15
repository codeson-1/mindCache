import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { title: '首页' },
  },
  {
    path: '/notes',
    name: 'NotesList',
    component: () => import('@/views/NotesList.vue'),
    meta: { title: '笔记列表' },
  },
  {
    path: '/notes/:id',
    name: 'NoteDetail',
    component: () => import('@/views/NoteDetail.vue'),
    meta: { title: '笔记详情' },
  },
  {
    path: '/create',
    name: 'TextInput',
    component: () => import('@/views/TextInput.vue'),
    meta: { title: '文字录入' },
  },
  {
    path: '/voice',
    name: 'VoiceInput',
    component: () => import('@/views/VoiceInput.vue'),
    meta: { title: '语音录入' },
  },
  {
    path: '/image',
    name: 'ImageInput',
    component: () => import('@/views/ImageInput.vue'),
    meta: { title: '图片录入' },
  },
  {
    path: '/search',
    name: 'SearchPage',
    component: () => import('@/views/SearchPage.vue'),
    meta: { title: '搜索' },
  },
  {
    path: '/summary',
    name: 'DailySummary',
    component: () => import('@/views/DailySummary.vue'),
    meta: { title: '每日摘要' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 动态修改 document.title
router.afterEach((to) => {
  document.title = `${to.meta.title} · MindCache`
})

export default router
