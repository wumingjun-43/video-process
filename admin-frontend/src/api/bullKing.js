import request from '@/utils/request'

// 分页查询牛王列表
export function pageBullKing(params) {
  return request.get('/bull-king', { params })
}

// 查询牛王详情
export function getBullKing(id) {
  return request.get(`/bull-king/${id}`)
}

// 新增牛王
export function addBullKing(data) {
  const formData = new FormData()
  formData.append('description', data.description || '')
  formData.append('battleRecord', data.battleRecord || '')
  if (data.images) {
    data.images.forEach(img => formData.append('images', img))
  }
  return request.post('/bull-king', formData)
}

// 更新牛王
export function updateBullKing(id, data) {
  const formData = new FormData()
  formData.append('description', data.description || '')
  formData.append('battleRecord', data.battleRecord || '')
  if (data.images) {
    data.images.forEach(img => formData.append('images', img))
  }
  // retainedUrls 序列化为 JSON 字符串
  if (data.retainedUrls) {
    formData.append('retainedUrls', JSON.stringify(data.retainedUrls))
  }
  return request.put(`/bull-king/${id}`, formData)
}

// 删除牛王
export function deleteBullKing(id) {
  return request.delete(`/bull-king/${id}`)
}

// 图片匹配牛王
export function matchBullKing(formData) {
  return request.post('/bull-king/match', formData)
}

// 特征分析
export function analyzeFeatures(knowledgeFileId) {
  return request.post('/bull-king/features', null, { params: { knowledgeFileId } })
}
