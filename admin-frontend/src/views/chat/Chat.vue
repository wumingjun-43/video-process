<template>
  <div class="chat-page">
    <el-row :gutter="20" style="height: calc(100vh - 120px)">
      <!-- 左侧：知识图谱选择面板 -->
      <el-col :span="6">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>知识图谱范围</span>
              <el-button link type="primary" size="small" @click="loadKnowledgeList">
                <el-icon><Refresh /></el-icon> 刷新
              </el-button>
            </div>
          </template>

          <div class="knowledge-list">
            <el-checkbox-group v-model="selectedKnowledgeIds">
              <el-scrollbar max-height="calc(100vh - 280px)">
                <div v-if="knowledgeList.length === 0" class="empty-tip">
                  暂无可用的知识文件
                </div>
                <el-checkbox
                  v-for="item in knowledgeList"
                  :key="item.id"
                  :label="item.id"
                  :value="item.id"
                  class="knowledge-item"
                >
                  <span class="knowledge-name">{{ item.filename }}</span>
                  <el-tag size="small" :type="statusTagType(item.status)">{{ statusText(item.status) }}</el-tag>
                </el-checkbox>
              </el-scrollbar>
            </el-checkbox-group>
          </div>

          <div class="panel-footer">
            <el-button type="primary" size="small" @click="selectAll" :disabled="knowledgeList.length === 0">
              全选
            </el-button>
            <el-button size="small" @click="deselectAll" :disabled="knowledgeList.length === 0">
              清空
            </el-button>
            <span class="count-text">已选 {{ selectedKnowledgeIds.length }} 个</span>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：对话区域 -->
      <el-col :span="18">
        <el-card shadow="never" class="chat-card">
          <!-- 对话气泡区域 -->
          <div ref="chatBodyRef" class="chat-body" v-loading="loadingMessages">
            <div v-if="messages.length === 0" class="empty-chat">
              <el-icon :size="60" color="#c0c4cc"><ChatDotRound /></el-icon>
              <p>欢迎使用智能对话</p>
              <p class="hint">请选择知识图谱范围，然后输入您的问题</p>
            </div>
            <div v-for="(msg, idx) in messages" :key="idx" class="message-row" :class="msg.role">
              <div class="avatar">
                <el-icon v-if="msg.role === 'user'" :size="28"><User /></el-icon>
                <el-icon v-else :size="28"><ChatDotRound /></el-icon>
              </div>
              <div class="bubble">
                <div class="bubble-content" v-html="formatMessage(msg.content)"></div>
                <div class="bubble-meta">
                  <span>{{ msg.time }}</span>
                  <span v-if="msg.role === 'ai' && msg.loading" class="typing">
                    <span>.</span><span>.</span><span>.</span>
                  </span>
                </div>
              </div>
            </div>
            <div ref="bottomRef"></div>
          </div>

          <!-- 输入区域 -->
          <div class="chat-input-area">
            <el-input
              v-model="question"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 4 }"
              placeholder="请输入您的问题..."
              resize="none"
              @keydown.enter.exact.prevent="handleSend"
              :disabled="asking"
            />
            <div class="input-actions">
              <span class="hint-text">按 Enter 发送</span>
              <el-button
                type="primary"
                :loading="asking"
                :disabled="!question.trim()"
                @click="handleSend"
              >
                发 送
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, User, Refresh } from '@element-plus/icons-vue'
import { getKnowledgeList } from '@/api/chat'

const chatBodyRef = ref(null)
const bottomRef = ref(null)

const question = ref('')
const asking = ref(false)
const loadingMessages = ref(false)
const messages = ref([])
const knowledgeList = ref([])
const selectedKnowledgeIds = ref([])

// 滚动到底部
function scrollToBottom() {
  nextTick(() => {
    if (chatBodyRef.value) {
      chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
    }
  })
}

// 格式化消息（简单 Markdown 处理）
function formatMessage(text) {
  if (!text) return ''
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
    .replace(/(\d\..+)/g, '<br>$1')
}

// 获取当前时间
function getTime() {
  const now = new Date()
  const h = String(now.getHours()).padStart(2, '0')
  const m = String(now.getMinutes()).padStart(2, '0')
  const s = String(now.getSeconds()).padStart(2, '0')
  return `${h}:${m}:${s}`
}

// 发送消息（流式 SSE）
async function handleSend() {
  if (!question.value.trim() || asking.value) return

  const userMsg = {
    role: 'user',
    content: question.value.trim(),
    time: getTime(),
  }
  messages.value.push(userMsg)
  scrollToBottom()

  // 添加 AI 流式消息（用 reactive 包裹确保嵌套属性变更能触发响应式更新）
  const aiMsg = reactive({
    role: 'ai',
    content: '',
    time: getTime(),
    loading: true,
    streamBuffer: '', // 流式缓冲
  })
  messages.value.push(aiMsg)
  scrollToBottom()

  asking.value = true
  const currentQuestion = question.value.trim()
  question.value = ''

  try {
    // 获取 token 并附加到请求头
    const token = localStorage.getItem('token')
    const headers = { 'Content-Type': 'application/json' }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    // 使用 fetch API 直接调用 SSE 接口
    const response = await fetch('/api/chat/ask-stream', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify({
        question: currentQuestion,
        knowledgeFileIds: selectedKnowledgeIds.value.length > 0 ? selectedKnowledgeIds.value : null,
      }),
    })

    if (!response.ok) {
      const errorText = await response.text()
      throw new Error(`HTTP ${response.status}: ${errorText}`)
    }

    // 打印响应头，方便排查 SSE 格式问题
    console.log('[SSE] Content-Type:', response.headers.get('Content-Type'))
    console.log('[SSE] Transfer-Encoding:', response.headers.get('Transfer-Encoding'))

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let fullAnswer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const text = decoder.decode(value, { stream: false })
      if (text.trim()) {
        console.log('[SSE] chunk:', JSON.stringify(text))
      }

      // SSE 标准格式：data: xxx\n\n
      const lines = text.split(/\r?\n/)
      for (let i = 0; i < lines.length; i++) {
        const line = lines[i]
        if (line.startsWith('data:')) {
          const content = line.substring(5).trim()
          if (content) {
            fullAnswer += content
            aiMsg.content = fullAnswer
            nextTick(scrollToBottom)
          }
        }
      }
    }

    // 完成
    aiMsg.loading = false
    aiMsg.content = fullAnswer
    console.log('[SSE] stream done, total length:', fullAnswer.length)

  } catch (err) {
    // 移除 AI 消息
    messages.value.pop()
    ElMessage.error('回答失败: ' + (err.message || err))
  } finally {
    asking.value = false
  }
}

// 知识文件标签样式
function statusTagType(status) {
  const map = { pending: 'info', processing: 'warning', done: 'success', error: 'danger' }
  return map[status] || 'info'
}

function statusText(status) {
  const map = { pending: '待处理', processing: '处理中', done: '已完成', error: '失败' }
  return map[status] || status
}

// 加载知识文件列表
async function loadKnowledgeList() {
  try {
    const data = await getKnowledgeList()
    knowledgeList.value = data || []
  } catch (err) {
    ElMessage.error('加载知识列表失败')
  }
}

// 全选 / 清空
function selectAll() {
  selectedKnowledgeIds.value = knowledgeList.value.map(k => k.id)
}

function deselectAll() {
  selectedKnowledgeIds.value = []
}

onMounted(() => {
  loadKnowledgeList()
})
</script>

<style lang="scss" scoped>
.chat-page {
  height: calc(100vh - 100px);
}

.panel-card {
  height: 100%;
  display: flex;
  flex-direction: column;

  :deep(.el-card__body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 12px 16px;
    overflow: hidden;
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .knowledge-list {
    flex: 1;
    overflow: hidden;

    .empty-tip {
      text-align: center;
      color: #909399;
      padding: 20px 0;
      font-size: 14px;
    }

    .knowledge-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 6px 8px;
      border-radius: 4px;
      margin-bottom: 2px;

      &:hover {
        background-color: #f5f7fa;
      }

      .knowledge-name {
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        margin-right: 8px;
        font-size: 13px;
      }
    }
  }

  .panel-footer {
    display: flex;
    align-items: center;
    gap: 8px;
    padding-top: 12px;
    border-top: 1px solid #ebeef5;
    margin-top: 12px;

    .count-text {
      flex: 1;
      text-align: right;
      color: #909399;
      font-size: 12px;
    }
  }
}

.chat-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0;

  :deep(.el-card__body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 0;
    overflow: hidden;
  }

  .chat-body {
    flex: 1;
    overflow-y: auto;
    padding: 20px;

    .empty-chat {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100%;
      color: #909399;

      .hint {
        margin-top: 8px;
        font-size: 14px;
      }
    }

    .message-row {
      display: flex;
      margin-bottom: 16px;
      align-items: flex-start;

      &.user {
        flex-direction: row-reverse;

        .bubble {
          background-color: #409eff;
          color: #fff;

          .bubble-meta {
            color: rgba(255, 255, 255, 0.7);
            flex-direction: row-reverse;
          }
        }
      }

      .avatar {
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background-color: #f0f2f5;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #409eff;
        flex-shrink: 0;
      }

      .bubble {
        margin: 0 12px;
        max-width: 75%;
        border-radius: 12px;
        padding: 12px 16px;
        background-color: #f0f2f5;
        color: #303133;

        .bubble-content {
          line-height: 1.6;
          font-size: 14px;
          word-break: break-word;
        }

        .bubble-meta {
          display: flex;
          justify-content: space-between;
          margin-top: 6px;
          font-size: 11px;
          color: #909399;

          .typing {
            color: #409eff;
            font-weight: bold;

            span {
              animation: blink 1.4s infinite both;

              &:nth-child(2) { animation-delay: 0.2s; }
              &:nth-child(3) { animation-delay: 0.4s; }
            }
          }
        }
      }
    }
  }

  .chat-input-area {
    border-top: 1px solid #ebeef5;
    padding: 16px 20px;

    :deep(.el-textarea__inner) {
      border-radius: 12px;
      resize: none !important;
    }

    .input-actions {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 12px;

      .hint-text {
        font-size: 12px;
        color: #909399;
      }
    }
  }
}

@keyframes blink {
  0%, 80%, 100% { opacity: 0; }
  40% { opacity: 1; }
}
</style>
