<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>牛王管理</span>
          <el-button type="primary" @click="handleAdd">新增牛王</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="描述/战绩" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="图片" width="120">
          <template #default="{ row }">
            <el-image v-if="row.images && row.images.length"
              :src="row.images[0]?.imageUrl" style="width: 80px; height: 80px;"
              fit="cover" :preview-src-list="row.images.map(i => i.imageUrl)" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="battleRecord" label="历史战绩" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size"
        :total="pagination.total" layout="total, prev, pager, next" @current-change="loadData"
        style="margin-top: 20px; justify-content: flex-end;" />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑牛王' : '新增牛王'" width="600px">
      <el-form ref="formRef" :model="form" label-width="100px">
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="历史战绩">
          <el-input v-model="form.battleRecord" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="图片">
          <el-upload ref="uploadRef" :auto-upload="false" :limit="4"
            list-type="picture-card" accept="image/*"
            :file-list="internalFileList"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove">
            <el-icon><Plus /></el-icon>
          </el-upload>
          <template #error>最多上传4张图片</template>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { pageBullKing, addBullKing, updateBullKing, deleteBullKing } from '@/api/bullKing'

const tableData = ref([])
const searchForm = reactive({ keyword: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const submitLoading = ref(false)
const formRef = ref(null)
const internalFileList = ref([])

const form = reactive({
  description: '',
  battleRecord: '',
  images: [],
})

// 回显图片的 URL 列表（status=done 的旧图片）
const retainedUrls = ref([])

// el-upload 文件变化时同步到 internalFileList
function handleFileChange(file, fileLists) {
  // 保留所有 status=done 的旧图片 url
  retainedUrls.value = fileLists.filter(f => f.status === 'done').map(f => f.url)
  internalFileList.value = fileLists
}

// el-upload 文件删除时同步
function handleFileRemove(file, fileLists) {
  retainedUrls.value = fileLists.filter(f => f.status === 'done').map(f => f.url)
  internalFileList.value = fileLists
}

// 监听对话框打开，初始化图片列表
watch(dialogVisible, (visible) => {
  if (visible) {
    retainedUrls.value = []
    if (!isEdit.value) {
      internalFileList.value = []
    }
  }
})

async function loadData() {
  try {
    const data = await pageBullKing({
      keyword: searchForm.keyword,
      page: pagination.page,
      size: pagination.size,
    })
    tableData.value = data.records
    pagination.total = data.total
  } catch (e) { /* ignore */ }
}

function resetSearch() {
  searchForm.keyword = ''
  pagination.page = 1
  loadData()
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  form.description = ''
  form.battleRecord = ''
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  editId.value = row.id
  form.description = row.description || ''
  form.battleRecord = row.battleRecord || ''
  // 回显已有图片
  internalFileList.value = (row.images || []).map((img, idx) => ({
    uid: -idx,
    name: `image_${idx}`,
    status: 'done',
    url: img.imageUrl,
  }))
  // 记录回显图片的 URL
  retainedUrls.value = (row.images || []).map(img => img.imageUrl)
  dialogVisible.value = true
}

async function handleSubmit() {
  submitLoading.value = true
  try {
    // 区分：新增的图片（有 raw）和回显的旧图片（只有 url）
    const newImages = internalFileList.value
      .filter(f => f.raw || f.originFileObj || f.file)
      .map(f => f.raw || f.originFileObj || f.file)
      .filter(Boolean)

    const data = {
      description: form.description,
      battleRecord: form.battleRecord,
      images: newImages,
      retainedUrls: retainedUrls.value, // 保留的旧图片 URL 列表
    }
    if (isEdit.value) {
      await updateBullKing(editId.value, data)
      ElMessage.success('更新成功')
    } else {
      await addBullKing(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    // error handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确定要删除该牛王吗？', '提示', { type: 'warning' })
  await deleteBullKing(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.page-container {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
}
</style>
