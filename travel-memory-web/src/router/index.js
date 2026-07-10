import { createRouter, createWebHistory } from 'vue-router'
import TripList from '../views/TripList.vue'
import TripCreate from '../views/TripCreate.vue'
import TripEdit from '../views/TripEdit.vue'
import TripTimeline from '../views/TripTimeline.vue'
import TripJourney from '../views/TripJourney.vue'
import MemoryCreate from '../views/MemoryCreate.vue'
import MemoryEdit from '../views/MemoryEdit.vue'
import TripMap from '../views/TripMap.vue'

const routes = [
  { path: '/', redirect: '/trips' },
  { path: '/trips', component: TripList },
  { path: '/trips/new', component: TripCreate },
  { path: '/trips/:id/edit', component: TripEdit, props: true },
  { path: '/trips/:id/journey', component: TripJourney, props: true },
  { path: '/trips/:id', component: TripTimeline, props: true },
  { path: '/trips/:id/memories/new', component: MemoryCreate, props: true },
  { path: '/trips/:tripId/memories/:memoryId/edit', component: MemoryEdit, props: true },
  { path: '/trips/:id/map', component: TripMap, props: true },
]

export default createRouter({
  history: createWebHistory(),
  routes,
})
