<template>
  <div class="page-container">
    <el-card>
      <template #header>牛王特征分析</template>
      <el-form :inline="true">
        <el-form-item label="知识图谱范围">
          <el-select v-model="selectedKnowledgeFile" placeholder="全部知识图谱" clearable style="width: 300px;">
            <el-option v-for="kf in knowledgeList" :key="kf.id" :label="kf.filename" :value="kf.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="analyzing" @click="handleAnalyze">开始分析</el-button>
        </el-form-item>
      </el-form>

      <div v-loading="analyzing" style="margin-top: 20px;">
        <el-card v-if="analysis" shadow="never" class="analysis-card">
          <el-row :gutter="20">
            <el-col :span="8" v-for="(item, key) in featureItems" :key="key">
              <div class="feature-item">
                <el-tag type="info" size="large" effect="dark">{{ item.label }}</el-tag>
                <p style="margin-top: 12px; white-space: pre-wrap; color: #606266;">
                  {{ item.getValue(analysis) || '暂无数据' }}
                </p>
              </div>
            </el-col>
          </el-row>
          <div v-if="analysis.aiSummary" style="margin-top: 24px; padding: 16px; background: #f5f7fa; border-radius: 4px;">
            <strong style="font-size: 16px;">AI 综合总结</strong>
            <p style="margin-top: 8px; white-space: pre-wrap;">{{ analysis.aiSummary }}</p>
          </div>
        </el-card>

        <el-empty v-else-if="!analyzing" :description="emptyDescription" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { analyzeFeatures } from '@/api/bullKing'
import { pageKnowledge } from '@/api/knowledge'

const selectedKnowledgeFile = ref(null)
const knowledgeList = ref([])
const analyzing = ref(false)
const analysis = ref(null)
const emptyDescription = '选择知识图谱范围后点击"开始分析"'

const featureItems = computed(() => [
  { label: '牛角特征', getValue: (a) => a.hornFeatures },
  { label: '牛头特征', getValue: (a) => a.headFeatures },
  { label: '牛眼特征', getValue: (a) => a.eyeFeatures },
  { label: '毛发特征', getValue: (a) => a.furFeatures },
  { label: '牛旋特征', getValue: (a) => a.swirlFeatures },
  { label: '牛脚特征', getValue: (a) => a.legFeatures },
])

async function loadKnowledgeList() {
  try {
    const data = await pageKnowledge({ page: 1, size: 100 })
    knowledgeList.value = data.records
  } catch (e) { /* ignore */ }
}

async function handleAnalyze() {
  analyzing.value = true
  analysis.value = null
  try {
    analysis.value = await analyzeFeatures(selectedKnowledgeFile.value)
    ElMessage.success('分析完成')
  } catch (e) {
    ElMessage.error('分析失败: ' + (e?.message || '未知错误'))
  } finally {
    analyzing.value = false
  }
}

onMounted(loadKnowledgeList)
</script>

<style lang="scss" scoped>
.analysis-card {
  .feature-item {
    margin-bottom: 16px;
  }
}
</style>
