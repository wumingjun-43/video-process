<template>
  <div class="face-match-page">
    <el-row :gutter="20">
      <!-- 左侧: 上传区域 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <span>上传人脸照片进行匹配</span>
          </template>

          <el-upload
            ref="uploadRef"
            class="match-upload"
            drag
            :auto-upload="false"
            :limit="1"
            accept="image/*"
            :on-change="handleFileChange"
            :file-list="fileList"
          >
            <el-icon :size="50" class="upload-icon"><Search /></el-icon>
            <div class="el-upload__text">拖拽人脸照片到此处，或点击上传</div>
            <template #tip>
              <div class="el-upload__tip">支持 JPG/PNG 格式，建议上传正面人脸照片</div>
            </template>
          </el-upload>

          <!-- 预览 -->
          <div v-if="previewUrl" class="preview-area">
            <img :src="previewUrl" alt="预览" />
          </div>

          <el-button
            type="primary"
            class="match-btn"
            :disabled="!selectedFile"
            :loading="matching"
            @click="handleMatch"
          >
            {{ matching ? '匹配中...' : '开始匹配' }}
          </el-button>
        </el-card>
      </el-col>

      <!-- 右侧: 结果展示 -->
      <el-col :span="14">
        <el-card shadow="never" v-if="result">
          <template #header>
            <span>匹配结果</span>
          </template>

          <!-- 匹配成功 -->
          <div v-if="result.isMatch" class="result-success">
            <el-result icon="success" title="匹配成功" :sub-title="result.aiAnalysis">
              <template #extra>
                <div class="result-detail">
                  <el-descriptions :column="1" border>
                    <el-descriptions-item label="匹配用户">
                      <el-tag type="success" size="large">{{ result.userName }}</el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="登录名">{{ result.loginName }}</el-descriptions-item>
                    <el-descriptions-item label="置信度">
                      <el-progress
                        :percentage="Math.round((result.confidenceScore || 0) * 100)"
                        :stroke-width="18"
                        :status="getProgressStatus(result.confidenceScore)"
                      />
                    </el-descriptions-item>
                  </el-descriptions>
                </div>
              </template>
            </el-result>
          </div>

          <!-- 匹配失败 -->
          <div v-else class="result-fail">
            <el-result icon="warning" title="未找到匹配" :sub-title="result.aiAnalysis">
              <template #extra>
                <div class="result-detail">
                  <el-descriptions :column="1" border>
                    <el-descriptions-item label="置信度">
                      <el-progress
                        :percentage="Math.round((result.confidenceScore || 0) * 100)"
                        :stroke-width="18"
                        status="exception"
                      />
                    </el-descriptions-item>
                  </el-descriptions>
                </div>
              </template>
            </el-result>
          </div>

          <!-- 候选列表 -->
          <div v-if="result.candidates && result.candidates.length > 0" class="candidates-section">
            <h4>候选用户列表 (按余弦相似度排序)</h4>
            <el-table :data="result.candidates" border size="small" max-height="300">
              <el-table-column type="index" label="排名" width="60" align="center" />
              <el-table-column label="用户ID" width="100">
                <template #default="{ row }">{{ row.userId }}</template>
              </el-table-column>
              <el-table-column prop="userName" label="姓名" width="120" />
              <el-table-column label="参考图片" width="80">
                <template #default="{ row }">
                  <el-image
                    v-if="row.faceImageUrl"
                    :src="row.faceImageUrl"
                    fit="cover"
                    style="width: 50px; height: 50px; border-radius: 4px"
                  />
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="余弦相似度" width="140">
                <template #default="{ row }">
                  <el-tag :type="getSimilarityTagType(row.cosineSimilarity)">
                    {{ formatPercent(row.cosineSimilarity) }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>

        <!-- 空状态 -->
        <el-card shadow="never" v-else>
          <el-result icon="info" title="等待匹配" sub-title="请在左侧上传人脸照片并点击开始匹配">
          </el-result>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { matchFace } from '@/api/faceRecognition'

const matching = ref(false)
const fileList = ref([])
const previewUrl = ref('')
const selectedFile = ref(null)
const result = ref(null)

function handleFileChange(file) {
  fileList.value = file ? [file] : []
  selectedFile.value = file.raw || file.file
  if (selectedFile.value) {
    previewUrl.value = URL.createObjectURL(selectedFile.value)
  }
}

async function handleMatch() {
  if (!selectedFile.value) {
    ElMessage.warning('请先上传人脸照片')
    return
  }

  matching.value = true
  result.value = null

  try {
    const formData = { image: selectedFile.value }
    const data = await matchFace(formData)
    result.value = data
    ElMessage.success('匹配完成')
  } catch (err) {
    ElMessage.error('匹配失败: ' + (err.message || err))
  } finally {
    matching.value = false
  }
}

function formatPercent(value) {
  if (value == null) return '0%'
  return Math.round(Number(value) * 100) + '%'
}

function getProgressStatus(score) {
  if (score == null) return ''
  const num = Number(score)
  if (num >= 0.8) return 'success'
  if (num >= 0.6) return 'warning'
  return 'exception'
}

function getSimilarityTagType(similarity) {
  if (similarity == null) return 'info'
  const num = Number(similarity)
  if (num >= 0.8) return 'success'
  if (num >= 0.6) return 'warning'
  return 'danger'
}
</script>

<style lang="scss" scoped>
.face-match-page {
  .match-upload {
    margin-bottom: 16px;

    :deep(.el-upload-dragger) {
      width: 100%;
    }
  }

  .upload-icon {
    color: #409eff;
  }

  .preview-area {
    margin: 16px 0;
    text-align: center;

    img {
      max-width: 100%;
      max-height: 300px;
      border-radius: 8px;
      border: 2px solid #ebeef5;
    }
  }

  .match-btn {
    width: 100%;
    height: 48px;
    font-size: 16px;
    margin-top: 8px;
  }

  .result-success,
  .result-fail {
    margin-bottom: 20px;
  }

  .result-detail {
    margin-top: 16px;
    width: 400px;
  }

  .candidates-section {
    h4 {
      margin-bottom: 12px;
      color: #606266;
    }
  }
}
</style>
