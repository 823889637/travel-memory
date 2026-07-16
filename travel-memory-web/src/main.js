import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { setUnauthorizedHandler } from './api/request'
import { authResolved, currentUser } from './auth'
import './styles/main.css'
import './styles/mobile-baseline.css'

let redirectingForUnauthorized = false
setUnauthorizedHandler(() => {
  const currentRoute = router.currentRoute.value
  currentUser.value = null
  authResolved.value = true
  if (currentRoute.meta.public || redirectingForUnauthorized) return
  redirectingForUnauthorized = true
  router.replace({ path: '/login', query: { redirect: currentRoute.fullPath } })
    .finally(() => { redirectingForUnauthorized = false })
})

createApp(App).use(router).mount('#app')
