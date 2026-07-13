import { ref } from 'vue'
import { getCsrf, getMe, logout as apiLogout } from './api/auth'
export const currentUser = ref(null)
export const authResolved = ref(false)
export async function loadCurrentUser () { try { currentUser.value = await getMe() } catch { currentUser.value = null } finally { authResolved.value = true } return currentUser.value }
export async function prepareCsrf () { await getCsrf() }
export async function signOut () { try { await apiLogout() } finally { currentUser.value = null } }
