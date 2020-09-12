<template>
  <div class="login_container">
    <div class="login_box">
    <div class="avatar">
      <img src="../assets/logo.png" alt="">
    </div>
    <el-form ref="loginFormRef" label-width="0px" class="login_form" :model="loginForm" :rules="loginFormRules">
      <el-form-item prop="userName" >
        <el-input v-model="loginForm.userName" prefix-icon="el-icon-s-custom"></el-input>
      </el-form-item>
      <el-form-item prop="password" >
        <el-input v-model="loginForm.password" prefix-icon="el-icon-lock" type="password"></el-input>
      </el-form-item>
      <el-form-item class="btns">
        <el-button type="primary" @click="login">登陆</el-button>
        <el-button type="info" @click="resetLoginForm">重置</el-button>
      </el-form-item>
    </el-form>
    </div>
  </div>
</template>

<script>
export default {
  data () {
    return {
      loginForm: {
        userName: '223',
        password: '123'
      },
      loginFormRules: {
        userName: [
          { required: true, message: '请输入登陆名称', trigger: 'blur' },
          { min: 3, max: 6, message: '长度在3到6', trigger: 'blur' }
        ],
        password: [
          { required: true, message: '请输入登陆密码', trigger: 'blur' },
          { min: 8, max: 16, message: '长度在8到16', trigger: 'blur' }
        ]
      }
    }
  },
  methods: {
    resetLoginForm () {
      this.$refs.loginFormRef.resetFields()
    },
    login () {
      this.$message.success('sds')
    }
  }
}
</script>
<style lang="less" scoped>
.login_container{
  background-color: #504545;
  height: 100%;
}
.login_box{
  width: 450px;
  height: 300px;
  background-color: rgb(37, 18, 18);
  border-radius: 3px;
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%,-50%);
}
.avatar{
  height: 130px;
  width: 130px;
  border: 1px solid #615656;
  border-radius: 50%;
  box-shadow: 0 0 10px #615656;
  position: absolute;
  left: 50%;
  transform: translate(-50%);
  img{
    width: 100%;
    height: 100%;
    border-radius: 50%;
  }
}
.btns{
  display: flex;
  justify-content: flex-end;
}
.login_form{
  position: absolute;
  bottom: 0%;
  width: 100%;
  padding: 0 20px;
  box-sizing: border-box;
}
</style>
