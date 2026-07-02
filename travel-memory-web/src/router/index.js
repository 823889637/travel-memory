import { createRouter, createWebHistory } from 'vue-router'
import TripList from '../views/TripList.vue'
import TripCreate from '../views/TripCreate.vue'
import TripTimeline from '../views/TripTimeline.vue'
import MemoryCreate from '../views/MemoryCreate.vue'
import TripMap from '../views/TripMap.vue'

const routes = [
  { path: '/', redirect: '/trips' },
  { path: '/trips', component: TripList },
  { path: '/trips/new', component: TripCreate },
  { path: '/trips/:id', component: TripTimeline, props: true },
  { path: '/trips/:id/memories/new', component: MemoryCreate, props: true },
  { path: '/trips/:id/map', component: TripMap, props: true },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
