import request from '@/utils/request'

export function login(data) {
  return request.post('/auth/login', data)
}

export function logout() {
  return request.post('/auth/logout')
}

/**
 * 人脸登录
 * @param {FormData} data - 包含 image 文件
 * @param {number} [threshold] - 可选的相似度阈值
 */
export function faceLogin(data, threshold) {
  const formData = new FormData()
  formData.append('image', data.image)
  if (threshold !== undefined && threshold !== null) {
    formData.append('threshold', threshold)
  }
  return request.post('/auth/face-login', formData)
}
