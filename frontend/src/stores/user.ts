import { defineStore } from 'pinia'
import { getToken, removeToken, setToken } from '@/utils/auth'
import { login as loginApi, getUserInfo as getUserInfoApi } from '@/api/auth'

type AnyUser = Record<string, any> | null

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userInfo: null as AnyUser,
  }),

  getters: {
    isLoggedIn: (state) => !!state.token && !!state.userInfo,
    roleType: (state) => (state.userInfo as any)?.roleType || 'EMPLOYEE',
    isAdmin: (state) => (state.userInfo as any)?.roleType === 'ADMIN',
    isEngineer: (state) => (state.userInfo as any)?.roleType === 'ENGINEER',
    isEngineerOrAdmin: (state) => {
      const role = (state.userInfo as any)?.roleType
      return role === 'ENGINEER' || role === 'ADMIN'
    },
    userId: (state) => (state.userInfo as any)?.id,
    username: (state) => (state.userInfo as any)?.username || (state.userInfo as any)?.realName || '',
  },

  actions: {
    async login(loginForm: { username: string; password: string }) {
      const res: any = await loginApi(loginForm)
      if (res.code == 200) {
        this.token = res.data.token
        setToken(res.data.token)
        const userData = res.data.user || res.data
        this.userInfo = userData
      } else {
        throw new Error(res.msg || '登录失败')
      }
    },

    async getUserInfo() {
      const res: any = await getUserInfoApi()
      if (res.code == 200) {
        this.userInfo = res.data
      } else {
        throw new Error(res.msg || '获取用户信息失败')
      }
    },

    async logout() {
      this.token = ''
      this.userInfo = null
      removeToken()
    },
  },
})