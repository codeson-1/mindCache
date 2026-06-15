<template>
  <el-container class="app-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="app-aside">
      <div class="logo" @click="$router.push('/')">
        <span class="logo-icon">🧠</span>
        <span v-show="!isCollapsed" class="logo-text">MindCache</span>
      </div>

      <el-menu
        :default-active="route.path"
        :collapse="isCollapsed"
        router
        class="nav-menu"
      >
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/create">
          <el-icon><EditPen /></el-icon>
          <span>文字录入</span>
        </el-menu-item>
        <el-menu-item index="/voice">
          <el-icon><Microphone /></el-icon>
          <span>语音录入</span>
        </el-menu-item>
        <el-menu-item index="/image">
          <el-icon><PictureFilled /></el-icon>
          <span>图片录入</span>
        </el-menu-item>
        <el-menu-item index="/notes">
          <el-icon><Document /></el-icon>
          <span>笔记列表</span>
        </el-menu-item>
        <el-menu-item index="/search">
          <el-icon><Search /></el-icon>
          <span>搜索</span>
        </el-menu-item>
        <el-menu-item index="/summary">
          <el-icon><DataAnalysis /></el-icon>
          <span>每日摘要</span>
        </el-menu-item>
      </el-menu>

      <!-- 折叠按钮 -->
      <div class="collapse-btn" @click="isCollapsed = !isCollapsed">
        <el-icon>
          <Fold v-if="!isCollapsed" />
          <Expand v-else />
        </el-icon>
      </div>
    </el-aside>

    <!-- 主内容 -->
    <el-container>
      <el-header class="app-header" height="56px">
        <h2 class="page-title">{{ route.meta.title }}</h2>
        <div class="header-right">
          <el-tag type="info" effect="plain" size="small">v1.0 MVP</el-tag>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const isCollapsed = ref(false)
</script>

<style scoped>
.app-container {
  height: 100vh;
}

.app-aside {
  background: #fff;
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  transition: width 0.25s;
  overflow: hidden;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}
.logo-icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}
.logo-text {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--el-color-primary);
  white-space: nowrap;
}

.nav-menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;
}

.collapse-btn {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-top: 1px solid var(--el-border-color-light);
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}
.collapse-btn:hover {
  color: var(--el-color-primary);
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
  padding: 0 24px;
}
.page-title {
  font-size: 1.125rem;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-main {
  background: var(--el-bg-color-page);
  padding: 24px;
  overflow-y: auto;
}
</style>
