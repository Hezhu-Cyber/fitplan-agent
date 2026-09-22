import { createRouter, createWebHistory } from 'vue-router'
import { hasValidSession } from '../auth'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: 'FitPlan｜AI 个性化健身计划',
      description: '基于循证健身知识库生成个性化训练计划的 Fitness RAG 应用'
    }
  },
  {
    path: '/auth',
    name: 'Auth',
    component: () => import('../views/AuthView.vue'),
    meta: {
      title: '登录｜FitPlan',
      description: '登录 FitPlan 个人训练空间'
    }
  },
  {
    path: '/workspace',
    name: 'Workspace',
    component: () => import('../views/PlanWorkspace.vue'),
    meta: {
      title: '个人训练计划｜FitPlan',
      description: 'FitPlan 个性化健身计划工作台',
      requiresAuth: true
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(to => {
  if (to.meta.title) document.title = to.meta.title
  if (to.meta.requiresAuth && !hasValidSession()) {
    return { name: 'Auth', query: { redirect: to.fullPath } }
  }
  if (to.name === 'Auth' && hasValidSession()) {
    const redirect = typeof to.query.redirect === 'string' && to.query.redirect.startsWith('/') && !to.query.redirect.startsWith('//')
      ? to.query.redirect
      : '/workspace'
    return redirect
  }
  return true
})

export default router
