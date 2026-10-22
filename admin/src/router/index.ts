import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AdminLayout from '../layouts/AdminLayout.vue'
import DashboardView from '../views/DashboardView.vue'
import LoginView from '../views/LoginView.vue'
import CategoryView from '../views/CategoryView.vue'
import ProductView from '../views/ProductView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    {
      path: '/',
      component: AdminLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'dashboard', component: DashboardView },
        { path: 'categories', name: 'categories', component: CategoryView },
        { path: 'products', name: 'products', component: ProductView }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth) {
    if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
    if (!auth.admin && !(await auth.hydrate())) return '/login'
    return true
  }
  if (to.path === '/login' && auth.token) {
    if (!auth.admin && !(await auth.hydrate())) return true
    return '/'
  }
  return true
})

export default router
