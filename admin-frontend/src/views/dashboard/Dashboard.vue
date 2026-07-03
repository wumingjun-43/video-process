<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="牛王总数" :value="stats.bullKingCount">
            <template #prefix>
              <el-icon :size="24" color="#409eff"><CollectionTag /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="用户总数" :value="stats.userCount">
            <template #prefix>
              <el-icon :size="24" color="#67c23a"><User /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="匹配次数" :value="stats.matchCount">
            <template #prefix>
              <el-icon :size="24" color="#e6a23c"><Search /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="知识文件" :value="stats.knowledgeCount">
            <template #prefix>
              <el-icon :size="24" color="#f56c6c"><Files /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="24">
        <el-card>
          <template #header>系统说明</template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="系统名称">牛王认证系统</el-descriptions-item>
            <el-descriptions-item label="版本">V1.0.0</el-descriptions-item>
            <el-descriptions-item label="后端框架">Spring Boot 3.2.5</el-descriptions-item>
            <el-descriptions-item label="前端框架">Vue 3 + Element Plus</el-descriptions-item>
            <el-descriptions-item label="AI引擎">spring-ai-alibaba (DashScope)</el-descriptions-item>
            <el-descriptions-item label="数据库">MySQL 8.0 + Redis 7.x</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { pageBullKing } from '@/api/bullKing'
import { pageUser } from '@/api/user'
import { pageKnowledge } from '@/api/knowledge'
import { pageMatchRecord } from '@/api/matchRecord'

const stats = ref({
  bullKingCount: 0,
  userCount: 0,
  matchCount: 0,
  knowledgeCount: 0,
})

onMounted(async () => {
  try {
    const [bk, u, k, mr] = await Promise.all([
      pageBullKing({ page: 1, size: 1 }),
      pageUser({ page: 1, size: 1 }),
      pageKnowledge({ page: 1, size: 1 }),
      pageMatchRecord({ page: 1, size: 1 }),
    ])
    stats.value.bullKingCount = bk.total
    stats.value.userCount = u.total
    stats.value.knowledgeCount = k.total
    stats.value.matchCount = mr.total
  } catch (e) {
    // Stats might fail if backend not running
  }
})
</script>

<style lang="scss" scoped>
.dashboard {
  .stat-card {
    text-align: center;
    padding: 10px 0;
  }
}
</style>
