import { createRouter, createWebHistory } from 'vue-router'

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
    path: '/workspace',
    name: 'Workspace',
    component: () => import('../views/PlanWorkspace.vue'),
    meta: {
      title: '个人训练计划｜FitPlan',
      description: 'FitPlan 个性化健身计划工作台'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫，设置文档标题
router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router
