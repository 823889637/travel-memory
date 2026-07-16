import { createRouter, createWebHistory } from 'vue-router'
import TripList from '../views/TripList.vue'
import TripCreate from '../views/TripCreate.vue'
import TripEdit from '../views/TripEdit.vue'
import TripTimeline from '../views/TripTimeline.vue'
import TripJourney from '../views/TripJourney.vue'
import MemoryCreate from '../views/MemoryCreate.vue'
import MemoryEdit from '../views/MemoryEdit.vue'
import MemoryDetail from '../views/MemoryDetail.vue'
import TripMap from '../views/TripMap.vue'
import TripRecap from '../views/TripRecap.vue'
import TripCompanions from '../views/TripCompanions.vue'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import ChangePassword from '../views/ChangePassword.vue'
import AdminUsers from '../views/AdminUsers.vue'
import { authResolved, currentUser, loadCurrentUser } from '../auth'

const routes = [
  { path: '/', redirect: '/trips' },
  { path: '/login', component: Login, meta: { public: true, mobileHeader: 'none' } },
  { path: '/register', component: Register, meta: { public: true, mobileHeader: 'none' } },
  { path: '/change-password', component: ChangePassword, meta: { mobileHeader: 'page' } },
  { path: '/admin/users', component: AdminUsers, meta: { admin: true, mobileHeader: 'page' } },
  { path: '/trips', component: TripList, meta: { mobileHeader: 'page' } },
  { path: '/trips/new', component: TripCreate, meta: { mobileHeader: 'page' } },
  { path: '/trips/:id/edit', component: TripEdit, props: true, meta: { mobileHeader: 'page' } },
  { path: '/trips/:id/journey', component: TripJourney, props: true, meta: { mobileHeader: 'page' } },
  { path: '/trips/:id/recap', component: TripRecap, props: true, meta: { mobileHeader: 'page' } },
  { path: '/trips/:id/companions', component: TripCompanions, props: true, meta: { mobileHeader: 'page' } },
  {
    path: '/trips/:id/favorites',
    redirect: route => ({ path: `/trips/${route.params.id}`, query: { ...route.query, favorite: 'true' } }),
  },
  { path: '/trips/:id', component: TripTimeline, props: true, meta: { mobileHeader: 'page' } },
  { path: '/trips/:id/memories/new', component: MemoryCreate, props: true, meta: { mobileHeader: 'page' } },
  { path: '/trips/:tripId/memories/:memoryId', component: MemoryDetail, props: true, meta: { mobileHeader: 'page' } },
  { path: '/trips/:tripId/memories/:memoryId/edit', component: MemoryEdit, props: true, meta: { mobileHeader: 'page' } },
  {
    path: '/trips/:id/map',
    component: TripMap,
    props: true,
    meta: { mobileHeader: 'page', preserveQueryScroll: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.path === from.path && to.meta.preserveQueryScroll) return false
    return { top: 0 }
  },
})
router.beforeEach(async (to) => {
  if (!authResolved.value) await loadCurrentUser()
  const user = currentUser.value
  if (to.meta.public) return user ? (user.mustChangePassword ? '/change-password' : '/trips') : true
  if (!user) return { path: '/login', query: { redirect: to.fullPath } }
  if (user.mustChangePassword && to.path !== '/change-password') return '/change-password'
  if (to.meta.admin && user.role !== 'ADMIN') return '/trips'
  return true
})
export default router
