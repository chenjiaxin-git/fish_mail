<template>
 <div class="business-page">
  <div class="page-heading">
<div>
<h2>经营看板</h2>
<p>上海时间 · 每30秒自动更新，页面隐藏时暂停</p>
</div>
<div>
<el-button @click="password=true">修改密码</el-button>
<el-button :loading="loading" @click="load(false)">刷新</el-button>
</div>
</div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
  <template v-if="data">
   <div class="metric-grid">
<el-card>
<span>今日订单数</span>
<strong>{{data.todayOrderCount}}</strong>
<small>今日创建的全部订单，包含已取消、已关闭</small>
</el-card>
<el-card>
<span>今日销售额（元）</span>
<strong>{{formatMoneyCent(data.todaySalesAmountCent)}}</strong>
<small>今日微信成功收款总额，未扣除人工退款</small>
</el-card>
<el-card>
<span>待处理订单</span>
<strong>{{data.pendingOrderCount}}</strong>
<small>跨日期统计以下四类待办</small>
</el-card>
</div>
   <h3>经营待办</h3>
<div class="task-grid">
<button v-for="task in tasks" :key="task.label" @click="router.push({path:'/orders',query:task.query})">
<span>{{task.label}}</span>
<strong>{{data[task.key]}}</strong>
<span>查看订单 →</span>
</button>
</div>
   <p class="muted">统计日期：{{data.date}} · 最近更新：{{formatDateTime(data.generatedAt)}}</p>
  </template>
<el-empty v-else-if="!loading&&!error" description="暂无数据" />
 </div>
<ChangePasswordDialog v-model="password" />
</template>
<script setup lang="ts">
import { ref, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import ChangePasswordDialog from '../components/ChangePasswordDialog.vue';
import { getDashboard } from '../api/business';
import type { Dashboard } from '../types/business';
import { formatMoneyCent, formatDateTime } from '../utils/format';
import { useVisiblePolling } from '../composables/useVisiblePolling';
const router = useRouter(), password = ref(false), loading = ref(false), error = ref(''), data = ref<Dashboard>();
let sequence = 0;
const tasks: {
    label: string;
    key: 'pendingQuoteCount' | 'pendingDeliveryCount' | 'pendingCompletionCount' | 'pendingRefundCount';
    query: Record<string, string>;
}[] = [{ label: '待报价', key: 'pendingQuoteCount', query: { status: 'CREATED' } }, { label: '待配送', key: 'pendingDeliveryCount', query: { status: 'PAID_WAIT_DELIVERY' } }, { label: '待完成', key: 'pendingCompletionCount', query: { status: 'DELIVERING' } }, { label: '待登记人工退款', key: 'pendingRefundCount', query: { status: 'CANCELLED', refundStatus: 'REQUIRED' } }];
async function load(silent: boolean) {
    const seq = ++sequence;
    loading.value = true;
    try {
        const result = await getDashboard(silent);
        if (seq === sequence) {
            data.value = result;
            error.value = '';
        }
    }
    catch (e) {
        if (seq === sequence)
            error.value = e instanceof Error ? e.message : '看板加载失败';
    }
    finally {
        if (seq === sequence)
            loading.value = false;
    }
}
useVisiblePolling(load);
onUnmounted(() => sequence++);
</script>
