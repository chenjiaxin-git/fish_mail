<template>
  <el-card>
    <template #header>管理后台</template>
    <el-result icon="success" title="登录成功" sub-title="欢迎使用生鲜水产管理后台">
      <template #extra>
        <el-button @click="changePasswordVisible = true">修改密码</el-button>
      </template>
    </el-result>
  </el-card>
  <ChangePasswordDialog v-model="changePasswordVisible" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ChangePasswordDialog from '../components/ChangePasswordDialog.vue'

const changePasswordVisible = ref(false)
</script>
