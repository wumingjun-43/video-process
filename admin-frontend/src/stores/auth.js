import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userName = ref(localStorage.getItem('userName') || '')
  const userId = ref(localStorage.getItem('userId') || '')

  function setToken(t, name, id) {
    token.value = t
    userName.value = name
    userId.value = id
    localStorage.setItem('token', t)
    localStorage.setItem('userName', name)
    localStorage.setItem('userId', id)
  }

  function logout() {
    token.value = ''
    userName.value = ''
    userId.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('userName')
    localStorage.removeItem('userId')
  }

  return { token, userName, userId, setToken, logout }
})
