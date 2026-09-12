import Cookies from 'js-cookie'

const TOKEN_KEY = 'haze_token'

export function getToken(): string | undefined {
  return Cookies.get(TOKEN_KEY)
}

export function setToken(token: string) {
  return Cookies.set(TOKEN_KEY, token, { expires: 7 })
}

export function removeToken() {
  return Cookies.remove(TOKEN_KEY)
}