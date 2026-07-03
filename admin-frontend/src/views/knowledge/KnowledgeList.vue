<template>
  <div class="page-container">
    <el-card>
      <template #header>知识图谱文件列表</template>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="filename" label="文件名" min-width="200" />
        <el-table-column prop="fileType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ row.fileType.toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="上传时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size"
        :total="pagination.total" layout="total, prev, pager, next" @current-change="loadData"
        style="margin-top: 20px; justify-content: flex-end;" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageKnowledge, deleteKnowledge } from '@/api/knowledge'

const tableData = ref([])
const pagination = ref({ page: 1, size: 10, total: 0 })

function statusTagType(status) {
  const map = { pending: 'info', processing: 'warning', done: 'success', error: 'danger' }
  return map[status] || 'info'
}

function statusText(status) {
  const map = { pending: '待处理', processing: '处理中', done: '已完成', error: '失败' }
  return map[status] || status
}

async function loadData() {
  try {
    const data = await pageKnowledge({ page: pagination.value.page, size: pagination.value.size })
    tableData.value = data.records
    pagination.value.total = data.total
  } catch (e) { /* ignore */ }
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确定删除该知识文件吗？', '提示', { type: 'warning' })
  await deleteKnowledge(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>
