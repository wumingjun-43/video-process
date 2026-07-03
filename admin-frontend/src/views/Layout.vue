<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <span v-if="!isCollapse">牛王认证系统</span>
        <span v-else>牛王</span>
      </div>
      <el-menu :default-active="activeMenu" :collapse="isCollapse" router background-color="#304156"
        text-color="#bfcbd9" active-text-color="#409eff" :collapse-transition="false">
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <el-menu-item index="/bull-king">
          <el-icon><CollectionTag /></el-icon>
          <span>牛王管理</span>
        </el-menu-item>
        <el-menu-item index="/bull-king/match">
          <el-icon><Search /></el-icon>
          <span>牛王匹配</span>
        </el-menu-item>
        <el-menu-item index="/bull-king/features">
          <el-icon><DataAnalysis /></el-icon>
          <span>特征分析</span>
        </el-menu-item>
        <el-menu-item index="/knowledge/upload">
          <el-icon><Upload /></el-icon>
          <span>知识上传</span>
        </el-menu-item>
        <el-menu-item index="/knowledge">
          <el-icon><Files /></el-icon>
          <span>知识图谱</span>
        </el-menu-item>
        <el-menu-item index="/user">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/match-record">
          <el-icon><Tickets /></el-icon>
          <span>匹配记录</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </div>
        <div class="header-right">
          <span class="username">{{ authStore.userName || '管理员' }}</span>
          <el-button link type="danger" @click="handleLogout">退出</el-button>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isCollapse = ref(false)

const activeMenu = computed(() => route.path)

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    .then(() => {
      authStore.logout()
      router.push('/login')
    })
    .catch(() => {})
}
</script>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background-color: #304156;
  transition: width 0.3s;
  overflow: hidden;

  .logo {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 18px;
    font-weight: bold;
    background-color: #263445;
  }

  :deep(.el-menu) {
    border-right: none;
  }
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 20px;

  .header-left {
    display: flex;
    align-items: center;
    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
    }
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 12px;
    .username {
      font-size: 14px;
      color: #606266;
    }
  }
}

.main-content {
  background-color: #f5f7fa;
  padding: 20px;
}
</style>
