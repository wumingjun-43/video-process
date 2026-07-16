import request from '@/utils/request'

/**
 * 智能对话（同步）
 * @param {object} data - { question, knowledgeFileIds }
 */
export function askChat(data) {
  return request.post('/chat/ask', data)
}

/**
 * 智能对话（流式 SSE）
 * @param {object} data - { question, knowledgeFileIds }
 * @returns {Promise<ReadableStream>}
 */
export function askChatStream(data) {
  return request.post('/chat/ask-stream', data, {
    responseType: 'stream',
  })
}

/**
 * 获取可检索的知识文件列表
 */
export function getKnowledgeList() {
  return request.get('/chat/knowledge-list')
}

/**
 * 获取最近对话历史
 * @param {number} limit - 返回条数
 */
export function getChatHistory(limit) {
  return request.get('/chat/history', { params: { limit } })
}
