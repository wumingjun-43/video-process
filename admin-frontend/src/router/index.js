import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/views/Layout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '首页', icon: 'Odometer' },
      },
      {
        path: 'bull-king',
        name: 'BullKingList',
        component: () => import('@/views/bullKing/BullKingList.vue'),
        meta: { title: '牛王管理', icon: 'CollectionTag' },
      },
      {
        path: 'bull-king/match',
        name: 'BullKingMatch',
        component: () => import('@/views/bullKing/BullKingMatch.vue'),
        meta: { title: '牛王匹配', icon: 'Search' },
      },
      {
        path: 'bull-king/features',
        name: 'BullKingFeature',
        component: () => import('@/views/bullKing/BullKingFeature.vue'),
        meta: { title: '特征分析', icon: 'DataAnalysis' },
      },
      {
        path: 'knowledge',
        name: 'KnowledgeList',
        component: () => import('@/views/knowledge/KnowledgeList.vue'),
        meta: { title: '知识图谱', icon: 'Files' },
      },
      {
        path: 'knowledge/upload',
        name: 'KnowledgeUpload',
        component: () => import('@/views/knowledge/KnowledgeUpload.vue'),
        meta: { title: '知识上传', icon: 'Upload' },
      },
      {
        path: 'user',
        name: 'UserManage',
        component: () => import('@/views/user/UserManage.vue'),
        meta: { title: '用户管理', icon: 'User' },
      },
      {
        path: 'match-record',
        name: 'MatchRecordList',
        component: () => import('@/views/matchRecord/MatchRecordList.vue'),
        meta: { title: '匹配记录', icon: 'Tickets' },
      },
      {
        path: 'face/register',
        name: 'FaceRegister',
        component: () => import('@/views/face/FaceRegister.vue'),
        meta: { title: '人脸注册', icon: 'UserFilled' },
      },
      {
        path: 'face/match',
        name: 'FaceMatch',
        component: () => import('@/views/face/FaceMatch.vue'),
        meta: { title: '人脸匹配', icon: 'Picture' },
      },
      {
        path: 'chat',
        name: 'SmartChat',
        component: () => import('@/views/chat/Chat.vue'),
        meta: { title: '智能对话', icon: 'ChatDotRound' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 导航守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
