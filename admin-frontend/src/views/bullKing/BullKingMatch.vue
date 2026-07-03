<template>
  <div class="page-container">
    <el-row :gutter="20">
      <!-- 左侧：上传图片 -->
      <el-col :span="10">
        <el-card>
          <template #header>上传图片进行匹配</template>
          <el-upload ref="uploadRef" class="match-upload" drag action=""
            :auto-upload="false" :limit="1" accept="image/*" v-model:file-list="fileList">
            <el-icon :size="50"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处 或 <em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 jpg/png 格式图片</div>
            </template>
          </el-upload>

          <el-form label-width="120px" style="margin-top: 20px;">
            <el-form-item label="选择知识图谱">
              <el-select v-model="selectedKnowledgeFile" placeholder="不选择则使用全部知识" clearable style="width: 100%">
                <el-option v-for="kf in knowledgeList" :key="kf.id" :label="kf.filename" :value="kf.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="matching" @click="handleMatch" style="width: 100%">
                开始匹配
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：匹配结果 -->
      <el-col :span="14">
        <el-card>
          <template #header>匹配结果</template>
          <div v-loading="matching" class="match-result">
            <el-empty v-if="!result && !matching" :description="emptyDesc" />

            <el-card v-else-if="result" shadow="never" class="result-card">
              <div class="result-header">
                <el-tag :type="result.confidenceScore >= 0.8 ? 'success' : result.confidenceScore >= 0.5 ? 'warning' : 'danger'"
                  size="large">
                  匹配置信度: {{ result.confidenceScore }}
                </el-tag>
              </div>

              <el-row :gutter="20" style="margin-top: 20px;">
                <el-col :span="10">
                  <div class="result-images">
                    <el-image v-for="(img, idx) in result.images" :key="idx" :src="img"
                      style="width: 100%; height: 180px; border-radius: 4px;" fit="cover" />
                  </div>
                </el-col>
                <el-col :span="14">
                  <div class="result-info">
                    <el-descriptions :column="1" border>
                      <el-descriptions-item label="牛王ID">{{ result.bullKingId }}</el-descriptions-item>
                      <el-descriptions-item label="描述">{{ result.description }}</el-descriptions-item>
                      <el-descriptions-item label="历史战绩">{{ result.battleRecord }}</el-descriptions-item>
                    </el-descriptions>
                    <div v-if="result.aiAnalysis" style="margin-top: 16px; padding: 12px; background: #f5f7fa; border-radius: 4px;">
                      <strong>AI 分析:</strong>
                      <p style="margin-top: 8px; white-space: pre-wrap;">{{ result.aiAnalysis }}</p>
                    </div>
                  </div>
                </el-col>
              </el-row>
            </el-card>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { matchBullKing } from '@/api/bullKing'
import { pageKnowledge } from '@/api/knowledge'

const fileList = ref([])
const selectedKnowledgeFile = ref(null)
const knowledgeList = ref([])
const matching = ref(false)
const result = ref(null)
const emptyDesc = '上传图片后点击"开始匹配"'

async function loadKnowledgeList() {
  try {
    const data = await pageKnowledge({ page: 1, size: 100 })
    knowledgeList.value = data.records
  } catch (e) { /* ignore */ }
}

async function handleMatch() {
  if (fileList.value.length === 0) {
    ElMessage.warning('请先上传图片')
    return
  }

  matching.value = true
  result.value = null
  try {
    const formData = new FormData()
    formData.append('image', fileList.value[0].raw)
    if (selectedKnowledgeFile.value) {
      formData.append('knowledgeFileId', selectedKnowledgeFile.value)
    }
    result.value = await matchBullKing(formData)
    ElMessage.success('匹配完成')
  } catch (e) {
    ElMessage.error('匹配失败: ' + (e?.message || '未知错误'))
  } finally {
    matching.value = false
  }
}

onMounted(loadKnowledgeList)
</script>

<style lang="scss" scoped>
.match-upload {
  :deep(.el-upload-dragger) {
    padding: 40px 20px;
  }
}

.result-card {
  .result-header {
    display: flex;
    justify-content: flex-end;
  }

  .result-images {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .result-info {
    :deep(.el-descriptions__label) {
      width: 100px;
    }
  }
}
</style>
