<template>
  <div class="page-container">
    <el-card>
      <template #header>匹配记录</template>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="bullKingId" label="匹配牛王ID" width="120" />
        <el-table-column prop="imageUrl" label="匹配图片" width="100">
          <template #default="{ row }">
            <el-image v-if="row.imageUrl"
              :src="row.imageUrl.replace('/upload/', '/upload/')"
              style="width: 60px; height: 60px;" fit="cover" />
          </template>
        </el-table-column>
        <el-table-column prop="confidenceScore" label="置信度" width="120">
          <template #default="{ row }">
            <el-progress :percentage="row.confidenceScore ? Math.round(row.confidenceScore * 100) : 0"
              :format="(val) => val + '%'"
              :stroke-width="18"
              :status="row.confidenceScore >= 0.8 ? 'success' : row.confidenceScore >= 0.5 ? 'warning' : 'exception'" />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="匹配时间" width="180" />
      </el-table>

      <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size"
        :total="pagination.total" layout="total, prev, pager, next" @current-change="loadData"
        style="margin-top: 20px; justify-content: flex-end;" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { pageMatchRecord } from '@/api/matchRecord'

const tableData = ref([])
const pagination = ref({ page: 1, size: 10, total: 0 })

async function loadData() {
  try {
    const data = await pageMatchRecord({ page: pagination.value.page, size: pagination.value.size })
    tableData.value = data.records
    pagination.value.total = data.total
  } catch (e) { /* ignore */ }
}

onMounted(loadData)
</script>
