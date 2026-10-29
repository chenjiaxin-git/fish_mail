<template>
<div class="business-page">
<div class="page-heading">
<div>
<el-button link @click="router.push({path:'/orders',query:route.query})">← 返回订单列表</el-button>
<h2>订单详情</h2>
</div>
<el-button :loading="loading" :disabled="busy||dialog!==''" @click="load">刷新详情</el-button>
</div>
<el-alert v-if="error" :title="error" type="error" :closable="false" />
<template v-if="order">
<el-card>
<div class="page-heading">
<div>
<h3>{{order.orderNo}}</h3>
<el-tag>{{orderStatuses[order.status]}}</el-tag> <el-tag v-if="order.refundStatus!=='NONE'" type="warning">{{refundStatuses[order.refundStatus]}}</el-tag>
</div>
<div class="action-row">
<el-button v-if="actions.quote" type="primary" @click="openQuote">{{order.status==='QUOTED'?'重新报价':'报价'}}</el-button>
<el-button v-if="actions.deliver" type="primary" :disabled="busy" @click="transition('deliver')">确认配送</el-button>
<el-button v-if="actions.complete" type="primary" :disabled="busy" @click="transition('complete')">确认完成</el-button>
<el-button v-if="actions.cancel" type="danger" :disabled="busy" @click="dialog='cancel';reason=''">取消订单</el-button>
<el-button v-if="actions.refund" type="warning" @click="dialog='refund';reference='';refundChecked=false">登记人工退款</el-button>
</div>
</div>
<el-descriptions :column="3" border>
<el-descriptions-item label="联系人">{{order.addressSnapshot.contactName}}</el-descriptions-item>
<el-descriptions-item label="手机号">{{order.addressSnapshot.phone}}</el-descriptions-item>
<el-descriptions-item label="最终金额">{{order.finalAmountCent==null?'尚未报价':formatMoneyCent(order.finalAmountCent)+' 元'}}</el-descriptions-item>
<el-descriptions-item label="地址" :span="3">{{order.addressSnapshot.detailAddress}}</el-descriptions-item>
<el-descriptions-item label="备注" :span="3">{{order.remark||'无'}}</el-descriptions-item>
<el-descriptions-item label="预估金额">{{formatMoneyCent(order.estimatedAmountCent)}} 元</el-descriptions-item>
<el-descriptions-item label="报价版本">{{order.quoteVersion}}</el-descriptions-item>
<el-descriptions-item label="付款截止">{{formatDateTime(order.payDeadlineAt)}}</el-descriptions-item>
<el-descriptions-item label="创建时间">{{formatDateTime(order.createdAt)}}</el-descriptions-item>
<el-descriptions-item label="付款时间">{{formatDateTime(order.paidAt)}}</el-descriptions-item>
<el-descriptions-item label="配送时间">{{formatDateTime(order.deliveredAt)}}</el-descriptions-item>
<el-descriptions-item label="完成时间">{{formatDateTime(order.completedAt)}}</el-descriptions-item>
<el-descriptions-item label="关闭时间">{{formatDateTime(order.closedAt)}}</el-descriptions-item>
<el-descriptions-item label="取消时间">{{formatDateTime(order.cancelledAt)}}</el-descriptions-item>
<el-descriptions-item label="取消原因">{{order.cancelReason||'—'}}</el-descriptions-item>
<el-descriptions-item label="退款凭证" :span="2">{{order.refundReference||'—'}}</el-descriptions-item>
</el-descriptions>
<h3>商品明细</h3>
<el-table :data="order.items">
<el-table-column prop="productName" label="商品" />
<el-table-column prop="specName" label="规格" />
<el-table-column label="单价（元）">
<template #default="{row}">{{formatMoneyCent(row.unitPriceCent)}} / {{row.unit}}</template>
</el-table-column>
<el-table-column label="预估数量">
<template #default="{row}">{{row.estimatedQuantity}} {{row.unit}}</template>
</el-table-column>
<el-table-column label="实称数量">
<template #default="{row}">{{row.actualQuantity ?? '—'}} {{row.actualQuantity==null?'':row.unit}}</template>
</el-table-column>
<el-table-column label="最终金额（元）">
<template #default="{row}">{{row.finalAmountCent==null?'—':formatMoneyCent(row.finalAmountCent)}}</template>
</el-table-column>
</el-table>
</el-card>
<el-card>
<h3>报价历史</h3>
<el-empty v-if="!order.priceRevisions.length" description="暂无报价" />
<el-collapse>
<el-collapse-item v-for="revision in order.priceRevisions" :key="revision.id" :title="'第'+revision.revisionNo+'次报价 · '+formatMoneyCent(revision.finalAmountCent)+'元 · '+formatDateTime(revision.createdAt)">
<p>{{revision.reason}}</p>
<el-table v-if="parseRevisions(revision.itemsSnapshot)" :data="parseRevisions(revision.itemsSnapshot)||[]">
<el-table-column prop="orderItemId" label="商品明细编号" />
<el-table-column prop="actualQuantity" label="实称数量" />
<el-table-column label="金额（元）">
<template #default="{row}">{{formatMoneyCent(row.finalAmountCent)}}</template>
</el-table-column>
</el-table>
<p v-else>历史明细格式无法展示，请联系管理员核查。</p>
</el-collapse-item>
</el-collapse>
</el-card>
<el-card>
<h3>支付记录</h3>
<el-table :data="order.payments" empty-text="暂无支付记录">
<el-table-column prop="outTradeNo" label="商户交易号" min-width="210" />
<el-table-column prop="transactionId" label="微信交易号" min-width="210" />
<el-table-column label="支付状态"><template #default="{row}">{{eventLabel(row.state)}}</template></el-table-column>
<el-table-column label="微信状态"><template #default="{row}">{{eventLabel(row.tradeState)}}</template></el-table-column>
<el-table-column label="金额（元）">
<template #default="{row}">{{formatMoneyCent(row.amountCent)}}</template>
</el-table-column>
<el-table-column label="支付时间" width="170">
<template #default="{row}">{{formatDateTime(row.paidAt)}}</template>
</el-table-column>
</el-table>
<h3>状态流水</h3>
<el-table :data="order.statusLogs">
<el-table-column label="状态变化" min-width="220">
<template #default="{row}">{{row.fromStatus?orderStatuses[row.fromStatus as OrderStatus]:'新订单'}} → {{orderStatuses[row.toStatus as OrderStatus]}}</template>
</el-table-column>
<el-table-column label="操作方"><template #default="{row}">{{eventLabel(row.operatorType)}}</template></el-table-column>
<el-table-column prop="reason" label="原因" min-width="200" />
<el-table-column label="来源"><template #default="{row}">{{eventLabel(row.source)}}</template></el-table-column>
<el-table-column label="时间" width="170">
<template #default="{row}">{{formatDateTime(row.createdAt)}}</template>
</el-table-column>
</el-table>
</el-card>
</template>
<el-dialog :model-value="dialog==='quote'" title="按实称数量报价" width="900px" :close-on-click-modal="false" :show-close="!busy" @close="close">
<el-alert v-if="conflict" type="error" title="订单状态或报价版本已变化。草稿已保留，请关闭窗口并刷新详情后重新报价。" :closable="false" />
<el-table :data="draft">
<el-table-column prop="name" label="商品 / 规格" />
<el-table-column label="实称数量">
<template #default="{row}">
<el-input v-model="row.quantity" :disabled="busy" />{{row.unit}}</template>
</el-table-column>
<el-table-column label="金额（元）">
<template #default="{row}">
<el-input v-model="row.amount" :disabled="busy" />
<el-button link @click="useReference(row)">按单价计算</el-button>
</template>
</el-table-column>
</el-table>
<p>报价合计：{{draftTotal}} 元</p>
<el-input v-model="reason" type="textarea" maxlength="500" show-word-limit placeholder="填写报价原因（必填）" />
<p class="muted">明细金额合计即为订单最终金额；零元订单无法发起微信支付。提交后用户需要确认报价。</p>
<template #footer>
<el-button :disabled="busy" @click="close">保留原订单并关闭</el-button>
<el-button type="primary" :loading="busy" :disabled="conflict" @click="submitQuote">提交报价</el-button>
</template>
</el-dialog>
<el-dialog :model-value="dialog==='cancel'" title="取消订单" width="560px" :close-on-click-modal="false" :show-close="!busy" @close="close">
<el-alert type="warning" :closable="false" :title="order?.status==='PAID_WAIT_DELIVERY'?'已支付订单取消后，需要在微信商户平台人工退款并登记凭证。':'涉及微信交易时将先查单、关单；结果不确定时订单会保留，请稍后重试。'" />
<el-input v-model="reason" class="dialog-input" type="textarea" maxlength="500" show-word-limit placeholder="取消原因（必填）" />
<template #footer>
<el-button :disabled="busy" @click="close">返回</el-button>
<el-button type="danger" :loading="busy" @click="submitCancel">确认取消</el-button>
</template>
</el-dialog>
<el-dialog :model-value="dialog==='refund'" title="登记人工退款" width="560px" :close-on-click-modal="false" :show-close="!busy" @close="close">
<el-alert title="此操作仅登记凭证。请先在微信商户平台完成原路退款。" type="warning" :closable="false" />
<el-input v-model="reference" class="dialog-input" maxlength="100" placeholder="微信商户平台退款凭证（必填）" />
<el-checkbox v-model="refundChecked">我已核实并完成微信商户平台人工退款</el-checkbox>
<template #footer>
<el-button :disabled="busy" @click="close">返回</el-button>
<el-button type="primary" :loading="busy" :disabled="!refundChecked" @click="submitRefund">确认登记</el-button>
</template>
</el-dialog>
</div>
</template>
<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getOrder, quoteOrder, orderAction } from '../api/business';
import type { OrderDetail, OrderStatus } from '../types/business';
import { orderStatuses, refundStatuses, allowedActions, yuanToCent, centToInput, validateQuantity, referenceAmount, sumCent, parseRevisions, eventLabel } from '../utils/business';
import { formatMoneyCent, formatDateTime } from '../utils/format';
const route = useRoute(), router = useRouter(), order = ref<OrderDetail>(), loading = ref(false), busy = ref(false), error = ref(''), dialog = ref(''), reason = ref(''), reference = ref(''), refundChecked = ref(false), conflict = ref(false);
let sequence = 0, version = 0;
interface Draft {
    id: number;
    name: string;
    kind: string;
    unit: string;
    price: number;
    quantity: string;
    amount: string;
}
const draft = ref<Draft[]>([]), actions = computed(() => order.value ? allowedActions(order.value.status, order.value.refundStatus) : {
    quote: false, deliver: false, complete: false, cancel: false, refund: false
}), draftTotal = computed(() => {
    try {
        return centToInput(sumCent(draft.value.map(x => yuanToCent(x.amount))));
    }
    catch {
        return '请输入有效金额';
    }
});
async function load() {
    const seq = ++sequence;
    loading.value = true;
    conflict.value = false;
    error.value = '';
    order.value = undefined;
    try {
        const result = await getOrder(Number(route.params.id));
        if (seq === sequence)
            order.value = result;
    }
    catch (e) {
        if (seq === sequence)
            error.value = e instanceof Error ? e.message : '详情加载失败';
    }
    finally {
        if (seq === sequence)
            loading.value = false;
    }
}
watch(() => route.params.id, () => {
    dialog.value = '';
    void load();
}, { immediate: true });
onUnmounted(() => sequence++);
function close() {
    if (!busy.value)
        dialog.value = '';
}
function openQuote() {
    if (conflict.value) {
        ElMessage.warning('请先刷新详情，再重新报价');
        return;
    }
    if (!order.value)
        return;
    version = order.value.quoteVersion;
    draft.value = order.value.items.map(x => ({
        id: x.id, name: x.productName + ' / ' + x.specName, kind: x.quantityKind, unit: x.unit, price: x.unitPriceCent, quantity: String(x.actualQuantity ?? x.estimatedQuantity), amount: centToInput(x.finalAmountCent ?? x.estimatedAmountCent)
    }));
    reason.value = '';
    conflict.value = false;
    dialog.value = 'quote';
}
function report(e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败');
}
function useReference(row: Draft) {
    try {
        validateQuantity(row.quantity, row.kind);
        row.amount = referenceAmount(row.quantity, row.price);
    }
    catch (e) {
        report(e);
    }
}
async function submitQuote() {
    if (!order.value || busy.value || conflict.value)
        return;
    try {
        if (!reason.value.trim())
            throw new Error('请填写报价原因');
        const items = draft.value.map(x => ({ orderItemId: x.id, actualQuantity: validateQuantity(x.quantity, x.kind), finalAmountCent: yuanToCent(x.amount) }));
        const total = sumCent(items.map(x => x.finalAmountCent));
        busy.value = true;
        order.value = await quoteOrder(order.value.id, {
            quoteVersion: version, items, finalAmountCent: total, reason: reason.value.trim()
        });
        dialog.value = '';
        ElMessage.success('报价已提交');
    }
    catch (e) {
        if (e && typeof e === 'object' && 'code' in e && ['QUOTE_VERSION_CONFLICT', 'ORDER_STATE_INVALID', 'ORDER_VERSION_CONFLICT'].includes(String(e.code)))
            conflict.value = true;
        report(e);
    }
    finally {
        busy.value = false;
    }
}
async function mutate(action: 'deliver' | 'complete' | 'cancel' | 'refund-confirmed', body?: object) {
    if (!order.value || busy.value)
        return;
    busy.value = true;
    try {
        order.value = await orderAction(order.value.id, action, body);
        dialog.value = '';
        ElMessage.success('操作成功');
    }
    catch (e) {
        report(e);
    }
    finally {
        busy.value = false;
    }
}
async function transition(action: 'deliver' | 'complete') {
    try {
        await ElMessageBox.confirm(action === 'deliver' ? '确认开始配送该订单？' : '确认该订单已配送完成？', '确认操作', { type: 'warning' });
        await mutate(action);
    }
    catch {
    }
}
function submitCancel() {
    if (!reason.value.trim()) {
        ElMessage.error('请填写取消原因');
        return;
    }
    void mutate('cancel', { reason: reason.value.trim() });
}
function submitRefund() {
    if (!refundChecked.value || !reference.value.trim()) {
        ElMessage.error('请核实退款并填写凭证');
        return;
    }
    void mutate('refund-confirmed', { refundReference: reference.value.trim() });
}
</script>
