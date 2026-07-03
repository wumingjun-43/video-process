<template>
  <div class="page-container">
    <el-card>
      <template #header>上传知识图谱文件</template>
      <el-upload ref="uploadRef" class="knowledge-upload" drag action="/api/knowledge/upload"
        :auto-upload="false" :limit="1" accept=".pdf,.doc,.docx,.xls,.xlsx"
        v-model:file-list="fileList" :on-change="handleFileChange">
        <el-icon :size="50"><Upload /></el-icon>
        <div class="el-upload__text">拖拽文件到此处 或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 PDF / Word / Excel 文件</div>
        </template>
      </el-upload>

      <el-button type="primary" :loading="uploading" style="margin-top: 20px; width: 100%;"
        @click="handleUpload">
        开始上传
      </el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { uploadKnowledge } from '@/api/knowledge'

const fileList = ref([])
const uploading = ref(false)

function handleFileChange(file) {
  fileList.value = [file]
}

async function handleUpload() {
  if (fileList.value.length === 0) {
    ElMessage.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', fileList.value[0].raw)
    await uploadKnowledge(formData)
    ElMessage.success('上传成功，文件正在处理中')
    fileList.value = []
  } catch (e) {
    ElMessage.error('上传失败: ' + (e?.message || '未知错误'))
  } finally {
    uploading.value = false
  }
}
</script>

<style lang="scss" scoped>
.knowledge-upload {
  :deep(.el-upload-dragger) {
    padding: 40px 20px;
  }
}
</style>
