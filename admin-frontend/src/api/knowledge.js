import request from '@/utils/request'

export function uploadKnowledge(formData) {
  return request.post('/knowledge/upload', formData)
}

export function pageKnowledge(params) {
  return request.get('/knowledge', { params })
}

export function deleteKnowledge(id) {
  return request.delete(`/knowledge/${id}`)
}
