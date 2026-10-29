<template>
  <el-container class="admin-layout">
    <el-header class="admin-header">
      <div>
        <strong>生鲜水产管理后台</strong>
        <span class="admin-name">{{ auth.admin?.username }}</span>
      </div>
      <el-button text @click="logout">退出登录</el-button>
    </el-header>
    <el-container>
      <el-aside width="180px" class="admin-aside">
        <el-menu router :default-active="route.path.startsWith('/orders') ? '/orders' : route.path" class="admin-menu">
          <el-menu-item index="/dashboard">经营看板</el-menu-item>
          <el-menu-item index="/orders">订单管理</el-menu-item>
          <el-menu-item index="/banners">轮播图管理</el-menu-item>
          <el-menu-item index="/categories">商品分类</el-menu-item>
          <el-menu-item index="/products">商品管理</el-menu-item>
        </el-menu>
      </el-aside>
      <el-main>
      <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

function logout(): void {
  auth.logout()
  router.replace('/login')
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-aside {
  background: #fff;
  border-right: 1px solid #e4e7ed;
}

.admin-menu {
  border-right: 0;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.admin-name {
  margin-left: 16px;
  color: #909399;
}
</style>
