<template>
  <div class="face-register-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>人脸注册管理</span>
          <el-button type="primary" @click="loadUsers">刷新列表</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="搜索">
          <el-input v-model="keyword" placeholder="姓名 / 登录名" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadUsers">搜索</el-button>
          <el-button @click="keyword = ''; loadUsers()">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 用户列表表格 -->
      <el-table :data="userList" border stripe v-loading="loading" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="loginName" label="登录名" width="150" />
        <el-table-column prop="age" label="年龄" width="80" />
        <el-table-column prop="genderText" label="性别" width="80" />
        <el-table-column label="人脸状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.hasFace" type="success" size="small">已注册</el-tag>
            <el-tag v-else type="info" size="small">未注册</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="人脸图片" width="100">
          <template #default="{ row }">
            <el-image
              v-if="row.hasFace && row.faceImageUrl"
              :src="row.faceImageUrl"
              :preview-src-list="[row.faceImageUrl]"
              fit="cover"
              style="width: 60px; height: 60px; border-radius: 4px"
            />
            <span v-else style="color: #ccc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              link
              @click="openRegisterDialog(row)"
            >
              {{ row.hasFace ? '重新注册' : '注册人脸' }}
            </el-button>
            <el-button
              v-if="row.hasFace"
              type="danger"
              size="small"
              link
              @click="handleRemoveFace(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 注册/重新注册弹窗 -->
      <el-dialog
        v-model="dialogVisible"
        :title="isEdit ? '重新注册人脸' : '注册人脸'"
        width="500px"
        @close="resetForm"
      >
        <div class="register-form">
          <el-descriptions :column="1" border size="small" style="margin-bottom: 20px">
            <el-descriptions-item label="姓名">{{ currentRow.name }}</el-descriptions-item>
            <el-descriptions-item label="登录名">{{ currentRow.loginName }}</el-descriptions-item>
          </el-descriptions>

          <el-form-label>上传人脸照片</el-form-label>
          <el-upload
            ref="uploadRef"
            class="face-upload"
            :auto-upload="false"
            :limit="1"
            accept="image/*"
            :on-change="handleFileChange"
            :file-list="fileList"
          >
            <el-icon :size="50"><Upload /></el-icon>
            <div class="el-upload__text">拖拽或点击上传人脸照片</div>
          </el-upload>

          <!-- 预览 -->
          <div v-if="previewUrl" class="preview">
            <img :src="previewUrl" alt="预览" />
          </div>
        </div>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleRegister" :loading="submitting">
            确认注册
          </el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { getUserFaces, registerFace, removeFace } from '@/api/faceRecognition'

const loading = ref(false)
const submitting = ref(false)
const keyword = ref('')
const userList = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentRow = reactive({})
const fileList = ref([])
const previewUrl = ref('')
const selectedFile = ref(null)
const uploadRef = ref(null)

// 加载用户列表
async function loadUsers() {
  loading.value = true
  try {
    const data = await getUserFaces()
    userList.value = data || []
  } catch (err) {
    ElMessage.error('加载用户列表失败: ' + (err.message || err))
  } finally {
    loading.value = false
  }
}

// 打开注册弹窗
function openRegisterDialog(row) {
  Object.assign(currentRow, row)
  isEdit.value = !!row.hasFace
  dialogVisible.value = true
  if (row.hasFace) {
    previewUrl.value = row.faceImageUrl
  }
}

// 文件选择
function handleFileChange(file) {
  fileList.value = file ? [file] : []
  selectedFile.value = file.raw || file.file
  if (selectedFile.value) {
    previewUrl.value = URL.createObjectURL(selectedFile.value)
  }
}

// 重置表单
function resetForm() {
  fileList.value = []
  previewUrl.value = ''
  selectedFile.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
}

// 提交注册
async function handleRegister() {
  if (!selectedFile.value) {
    ElMessage.warning('请先上传人脸照片')
    return
  }

  submitting.value = true
  try {
    const formData = {
      userId: currentRow.id,
      faceImage: selectedFile.value,
    }
    await registerFace(formData)
    ElMessage.success(isEdit.value ? '人脸重新注册成功' : '人脸注册成功')
    dialogVisible.value = false
    resetForm()
    loadUsers()
  } catch (err) {
    ElMessage.error('注册失败: ' + (err.message || err))
  } finally {
    submitting.value = false
  }
}

// 删除人脸
async function handleRemoveFace(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户「${row.name}」的人脸数据吗？此操作不可恢复。`,
      '确认删除',
      { type: 'warning' }
    )
    await removeFace(row.id)
    ElMessage.success('人脸已删除')
    loadUsers()
  } catch (err) {
    // 用户取消或不抛出异常
    if (err !== 'cancel') {
      ElMessage.error('删除失败: ' + (err.message || err))
    }
  }
}

// 初始化加载
loadUsers()
</script>

<style lang="scss" scoped>
.face-register-page {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .search-form {
    margin-bottom: 16px;
  }

  .face-upload {
    :deep(.el-upload) {
      border: 2px dashed #d9d9d9;
      border-radius: 8px;
      cursor: pointer;
      background-color: #fafafa;
      width: 178px;
      height: 178px;
      display: flex;
      align-items: center;
      justify-content: center;

      &:hover {
        border-color: #409eff;
      }
    }

    :deep(.el-upload-dragger) {
      width: 178px;
      height: 178px;
    }
  }

  .preview {
    margin-top: 16px;
    text-align: center;

    img {
      max-width: 200px;
      max-height: 200px;
      border-radius: 8px;
      border: 2px solid #ebeef5;
    }
  }
}
</style>
