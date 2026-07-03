import request from '@/utils/request'

export function pageUser(params) {
  return request.get('/user', { params })
}

export function getUser(id) {
  return request.get(`/user/${id}`)
}

export function addUser(data) {
  return request.post('/user', data)
}

export function updateUser(id, data) {
  return request.put(`/user/${id}`, data)
}

export function deleteUser(id) {
  return request.delete(`/user/${id}`)
}
