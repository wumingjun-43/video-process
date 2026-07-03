<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" @click="handleAdd">新增用户</el-button>
        </div>
      </template>

      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="姓名/登录名" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">搜索</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="loginName" label="登录名" width="150" />
        <el-table-column prop="age" label="年龄" width="80" />
        <el-table-column prop="genderText" label="性别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.gender === 1 ? '' : 'success'" size="small">{{ row.genderText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="pagination.page" v-model:page-size="pagination.size"
        :total="pagination.total" layout="total, prev, pager, next" @current-change="loadData"
        style="margin-top: 20px; justify-content: flex-end;" />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="登录名" prop="loginName">
          <el-input v-model="form.loginName" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="密码" :prop="isEdit ? '' : 'password'">
          <el-input v-model="form.password" type="password" show-password
            :placeholder="isEdit ? '留空则不修改密码' : '请输入密码'" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="年龄" prop="age">
              <el-input-number v-model="form.age" :min="1" :max="150" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="form.gender">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="0">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageUser, addUser, updateUser, deleteUser } from '@/api/user'

const tableData = ref([])
const searchForm = reactive({ keyword: '' })
const pagination = reactive({ page: 1, size: 10, total: 0 })
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const submitLoading = ref(false)
const formRef = ref(null)

const form = reactive({
  name: '',
  loginName: '',
  password: '',
  age: 25,
  gender: 1,
})

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  loginName: [{ required: true, message: '请输入登录名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  age: [{ required: true, message: '请输入年龄', trigger: 'change' }],
}

async function loadData() {
  try {
    const data = await pageUser({ keyword: searchForm.keyword, page: pagination.page, size: pagination.size })
    tableData.value = data.records
    pagination.total = data.total
  } catch (e) { /* ignore */ }
}

function resetSearch() {
  searchForm.keyword = ''
  pagination.page = 1
  loadData()
}

function handleAdd() {
  isEdit.value = false
  editId.value = null
  form.name = ''
  form.loginName = ''
  form.password = ''
  form.age = 25
  form.gender = 1
  dialogVisible.value = true
}

function handleEdit(row) {
  isEdit.value = true
  editId.value = row.id
  form.name = row.name
  form.loginName = row.loginName
  form.password = ''
  form.age = row.age
  form.gender = row.gender
  dialogVisible.value = true
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitLoading.value = true
  try {
    const data = {
      name: form.name,
      loginName: form.loginName,
      age: form.age,
      gender: form.gender,
    }
    if (!isEdit.value || form.password) {
      data.password = form.password
    }

    if (isEdit.value) {
      await updateUser(editId.value, data)
      ElMessage.success('更新成功')
    } else {
      await addUser(data)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    // error handled by interceptor
  } finally {
    submitLoading.value = false
  }
}

async function handleDelete(id) {
  await ElMessageBox.confirm('确定要删除该用户吗？', '提示', { type: 'warning' })
  await deleteUser(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
