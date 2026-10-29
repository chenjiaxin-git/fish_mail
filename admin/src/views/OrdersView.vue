<template>
<div class="business-page">
<div class="page-heading">
<div>
<h2>订单管理</h2>
<p>按创建日期筛选 · 页面可见时每30秒刷新</p>
</div>
<div>
<el-button :loading="loading" @click="load(false)">刷新</el-button>
<el-button :loading="exporting" @click="download">导出 Excel</el-button>
</div>
</div>
<el-card>
<el-form inline @submit.prevent="search">
<el-form-item label="状态">
<el-select v-model="filters.status" clearable style="width:160px">
<el-option v-for="(label,value) in orderStatuses" :key="value" :label="label" :value="value" />
</el-select>
</el-form-item>
<el-form-item label="退款">
<el-select v-model="filters.refundStatus" clearable style="width:170px">
<el-option v-for="(label,value) in refundStatuses" :key="value" :label="label" :value="value" />
</el-select>
</el-form-item>
<el-form-item label="订单号">
<el-input v-model="filters.orderNo" maxlength="40" placeholder="完整订单号" clearable />
</el-form-item>
<el-form-item label="手机号">
<el-input v-model="filters.phone" maxlength="32" placeholder="完整手机号" clearable />
</el-form-item>
<el-form-item label="创建日期">
<el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
</el-form-item>
<el-form-item>
<el-button type="primary" native-type="submit">查询</el-button>
<el-button @click="reset">重置</el-button>
</el-form-item>
</el-form>
<el-alert v-if="error" :title="error" type="error" :closable="false" />
<el-table :data="rows" v-loading="loading" empty-text="暂无符合条件的订单">
<el-table-column prop="orderNo" label="订单号" min-width="210">
<template #default="{row}">
<router-link :to="{path:'/orders/'+row.id,query:route.query}">{{row.orderNo}}</router-link>
</template>
</el-table-column>
<el-table-column label="状态" width="130">
<template #default="{row}">{{orderStatuses[row.status as OrderStatus]}}</template>
</el-table-column>
<el-table-column prop="contactName" label="联系人" width="100" />
<el-table-column prop="phone" label="手机号" width="140" />
<el-table-column label="金额（元）" width="130">
<template #default="{row}">{{formatMoneyCent(row.finalAmountCent ?? row.estimatedAmountCent)}}<small v-if="row.finalAmountCent==null" class="muted">（预估）</small>
</template>
</el-table-column>
<el-table-column label="退款" min-width="150">
<template #default="{row}">{{refundStatuses[row.refundStatus as RefundStatus]}}</template>
</el-table-column>
<el-table-column label="创建时间" width="170">
<template #default="{row}">{{formatDateTime(row.createdAt)}}</template>
</el-table-column>
<el-table-column label="操作" width="90" fixed="right">
<template #default="{row}">
<el-button link type="primary" @click="router.push({path:'/orders/'+row.id,query:route.query})">查看</el-button>
</template>
</el-table-column>
</el-table>
<el-pagination class="pagination" v-model:current-page="filters.page" v-model:page-size="filters.pageSize" :page-sizes="[20,50,100]" :total="total" layout="total, sizes, prev, pager, next" @current-change="apply" @size-change="search" />
<p class="muted">最近更新：{{updated || '尚未更新'}} · 导出使用当前已查询条件，须选择不超过31天的日期范围。</p>
</el-card>
</div>
</template>
<script setup lang="ts">
import { ref, reactive, watch, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getOrders, exportOrders, filterQuery } from '../api/business';
import type { OrderListItem, OrderStatus, RefundStatus } from '../types/business';
import { parseOrderFilters, orderStatuses, refundStatuses, validateExportDates } from '../utils/business';
import { formatMoneyCent, formatDateTime } from '../utils/format';
import { useVisiblePolling } from '../composables/useVisiblePolling';
const route = useRoute(), router = useRouter(), filters = reactive(parseOrderFilters(route.query)), dates = ref<string[] | null>(filters.startDate && filters.endDate ? [filters.startDate, filters.endDate] : null), rows = ref<OrderListItem[]>([]), total = ref(0), loading = ref(false), exporting = ref(false), error = ref(''), updated = ref('');
let sequence = 0;
async function load(silent: boolean) {
    const seq = ++sequence;
    loading.value = true;
    try {
        const result = await getOrders(parseOrderFilters(route.query), silent);
        if (seq === sequence) {
            rows.value = result.items;
            total.value = result.total;
            error.value = '';
            updated.value = new Date().toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' });
        }
    }
    catch (e) {
        if (seq === sequence)
            error.value = e instanceof Error ? e.message : '订单加载失败';
    }
    finally {
        if (seq === sequence)
            loading.value = false;
    }
}
async function apply() {
    filters.startDate = dates.value?.[0] || '';
    filters.endDate = dates.value?.[1] || '';
    const query = Object.fromEntries(new URLSearchParams(filterQuery(filters)));
    if (filterQuery(query) === filterQuery(route.query))
        await load(false);
    else
        await router.replace({ path: '/orders', query });
}
function search() {
    filters.page = 1;
    void apply();
}
function reset() {
    Object.assign(filters, parseOrderFilters({}));
    dates.value = null;
    void apply();
}
watch(() => route.query, () => {
    Object.assign(filters, parseOrderFilters(route.query));
    dates.value = filters.startDate && filters.endDate ? [filters.startDate, filters.endDate] : null;
    void load(false);
});
async function download() {
    try {
        const { page: _, pageSize: __, ...params } = parseOrderFilters(route.query);
        validateExportDates(params.startDate, params.endDate);
        exporting.value = true;
        const blob = await exportOrders(params);
        const url = URL.createObjectURL(blob), a = document.createElement('a');
        a.href = url;
        a.download = '订单_' + params.startDate + '_' + params.endDate + '.xlsx';
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }
    catch (e) {
        ElMessage.error(e instanceof Error ? e.message : '导出失败');
    }
    finally {
        exporting.value = false;
    }
}
useVisiblePolling(load);
onUnmounted(() => sequence++);
</script>
