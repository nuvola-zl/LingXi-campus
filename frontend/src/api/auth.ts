import request from '@/utils/request'

export function login(data: { username: string; password: string }) {
  return request.post('/user/login', data)
}

export function getUserInfo() {
  return request.get('/user/me')
}