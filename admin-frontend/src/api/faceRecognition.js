import request from '@/utils/request'

/**
 * 注册用户人脸
 */
export function registerFace(data) {
  const formData = new FormData()
  formData.append('userId', data.userId)
  formData.append('faceImage', data.faceImage)
  return request.post('/face/register', formData)
}

/**
 * 删除用户人脸
 */
export function removeFace(userId) {
  return request.delete(`/face/${userId}`)
}

/**
 * 人脸匹配
 */
export function matchFace(data) {
  const formData = new FormData()
  formData.append('image', data.image)
  return request.post('/face/match', formData)
}

/**
 * 获取所有已注册用户人脸列表
 */
export function getUserFaces() {
  return request.get('/face/users')
}
