import request from '@/utils/request'

export function pageMatchRecord(params) {
  return request.get('/match-record', { params })
}
