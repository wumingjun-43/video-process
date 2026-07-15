<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2 class="login-title">牛王认证系统</h2>

      <el-tabs v-model="activeTab" class="login-tabs">
        <!-- 密码登录 Tab -->
        <el-tab-pane label="账号密码登录" name="password">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="0">
            <el-form-item prop="loginName">
              <el-input v-model="form.loginName" placeholder="请输入登录名称" :prefix-icon="User" size="large" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock"
                size="large" show-password @keyup.enter="handlePasswordLogin" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" size="large" style="width: 100%" @click="handlePasswordLogin">
                登 录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 人脸登录 Tab -->
        <el-tab-pane label="人脸登录" name="face">
          <div class="face-login-area">
            <div ref="videoContainer" class="video-container">
              <video ref="videoEl" autoplay playsinline class="live-video"></video>
              <canvas ref="canvasEl" class="capture-canvas" hidden></canvas>
            </div>
            <div class="face-login-actions">
              <el-button type="primary" :loading="loading" size="large" style="width: 100%" @click="handleFaceLogin">
                开始人脸登录
              </el-button>
              <p class="face-hint">请将正脸对准摄像头，确保光线充足</p>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Camera } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { login, faceLogin } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref(null)
const loading = ref(false)
const activeTab = ref('password')

// 密码登录表单
const form = reactive({
  loginName: 'admin',
  password: 'admin123',
})

const rules = {
  loginName: [{ required: true, message: '请输入登录名称', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// 人脸登录相关
const videoEl = ref(null)
const canvasEl = ref(null)
const videoStream = ref(null)

async function handlePasswordLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login(form)
    authStore.setToken(data.token, data.userName, data.userId)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (err) {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

async function startCamera() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { width: 640, height: 480, facingMode: 'user' }
    })
    videoEl.value.srcObject = stream
    videoStream.value = stream
  } catch (err) {
    throw new Error('无法访问摄像头: ' + (err.message || '请确保已授予摄像头权限'))
  }
}

function stopCamera() {
  if (videoStream.value) {
    videoStream.value.getTracks().forEach(track => track.stop())
    videoStream.value = null
  }
  if (videoEl.value) {
    videoEl.value.srcObject = null
  }
}

function captureFrame() {
  const video = videoEl.value
  const canvas = canvasEl.value
  canvas.width = video.videoWidth
  canvas.height = video.videoHeight
  const ctx = canvas.getContext('2d')
  ctx.drawImage(video, 0, 0)
  return canvas.toDataURL('image/jpeg', 0.8)
}

async function handleFaceLogin() {
  if (activeTab.value !== 'face') return

  loading.value = true
  try {
    // 启动摄像头
    await startCamera()

    // 等待一帧稳定
    await new Promise(resolve => setTimeout(resolve, 500))

    // 捕获帧
    const dataUrl = captureFrame()
    const blob = await (await fetch(dataUrl)).blob()
    const file = new File([blob], 'face-login.jpg', { type: 'image/jpeg' })

    // 发送人脸登录请求
    const formData = { image: file }
    const data = await faceLogin(formData)
    authStore.setToken(data.token, data.userName, data.userId)
    ElMessage.success(`人脸登录成功，相似度: ${(data.similarity * 100).toFixed(1)}%`)
    stopCamera()
    router.push('/')
  } catch (err) {
    stopCamera()
    // error handled by interceptor for 401/500
    if (!err.message?.includes('401') && !err.message?.includes('请求失败')) {
      ElMessage.error(err.message || '人脸登录失败')
    }
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => {
  stopCamera()
})
</script>

<style lang="scss" scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 420px;
  padding: 20px;

  .login-title {
    text-align: center;
    margin-bottom: 30px;
    color: #303133;
    font-size: 24px;
  }

  :deep(.el-tabs__header) {
    margin-bottom: 20px;
  }

  :deep(.el-tabs__nav-wrap) {
    padding: 0 20px;
  }
}

.face-login-area {
  padding: 0 10px;

  .video-container {
    position: relative;
    width: 100%;
    aspect-ratio: 4 / 3;
    background: #000;
    border-radius: 8px;
    overflow: hidden;
    margin-bottom: 16px;

    .live-video {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .capture-canvas {
      display: none;
    }
  }

  .face-login-actions {
    .face-hint {
      text-align: center;
      color: #909399;
      font-size: 12px;
      margin-top: 8px;
      margin-bottom: 0;
    }
  }
}
</style>
