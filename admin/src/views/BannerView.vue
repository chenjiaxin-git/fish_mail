<template>
<div class="business-page">
<div class="page-heading">
<div>
<h2>轮播图管理</h2>
<p>数字越小越靠前，同排序按编号正序展示</p>
</div>
<el-button type="primary" @click="open()">新增轮播图</el-button>
</div>
<el-card>
<div class="filter-row">
<el-select v-model="status" clearable placeholder="全部状态" style="width:160px" @change="page=1;load()">
<el-option label="启用" value="ACTIVE" />
<el-option label="停用" value="DISABLED" />
</el-select>
<el-button :loading="loading" @click="load">刷新</el-button>
</div>
<el-alert v-if="error" :title="error" type="error" :closable="false" />
<el-table :data="rows" v-loading="loading" empty-text="暂无轮播图">
<el-table-column label="图片" width="200">
<template #default="{row}">
<el-image :src="row.imageUrl" class="banner-thumbnail" fit="cover">
<template #error>图片加载失败</template>
</el-image>
</template>
</el-table-column>
<el-table-column prop="title" label="标题" min-width="180" />
<el-table-column label="跳转目标" min-width="180">
<template #default="{row}">{{jumpLabels[row.jumpType as BannerRequest['jumpType']]}} {{row.targetName}} <el-tag v-if="!row.targetAvailable" type="warning">目标不可用</el-tag>
</template>
</el-table-column>
<el-table-column prop="sort" label="排序" width="80" />
<el-table-column label="状态" width="90">
<template #default="{row}">
<el-tag :type="row.status==='ACTIVE'?'success':'info'">{{row.status==='ACTIVE'?'启用':'停用'}}</el-tag>
</template>
</el-table-column>
<el-table-column label="操作" width="180">
<template #default="{row}">
<el-button link type="primary" @click="open(row.id)">编辑</el-button>
<el-button link type="primary" @click="toggle(row)">{{row.status==='ACTIVE'?'停用':'启用'}}</el-button>
<el-button link type="danger" @click="remove(row)">删除</el-button>
</template>
</el-table-column>
</el-table>
<el-pagination class="pagination" v-model:current-page="page" :page-size="20" :total="total" layout="total,prev,pager,next" @current-change="load" />
</el-card>
<el-dialog v-model="visible" :title="editing?'编辑轮播图':'新增轮播图'" width="640px" :close-on-click-modal="false" :show-close="!saving&&!uploading" :before-close="beforeClose">
<el-form label-width="90px" :disabled="saving">
<el-form-item label="标题" required>
<el-input v-model="form.title" maxlength="100" show-word-limit />
</el-form-item>
<el-form-item label="图片" required>
<div>
<el-image v-if="form.imageUrl" :src="form.imageUrl" class="banner-preview" fit="contain" />
<el-upload :show-file-list="false" accept="image/jpeg,image/png,image/webp" :http-request="upload" :disabled="uploading">
<el-button :loading="uploading">上传图片</el-button>
</el-upload>
<p class="muted">JPG / PNG / WebP，最大5 MB；仅使用上传返回的图片。</p>
</div>
</el-form-item>
<el-form-item label="跳转类型">
<el-select v-model="form.jumpType" @change="changeType">
<el-option v-for="(label,value) in jumpLabels" :key="value" :label="label" :value="value" />
</el-select>
</el-form-item>
<el-form-item v-if="form.jumpType!=='NONE'" label="跳转目标" required>
<div style="width:100%">
<el-input v-if="form.jumpType==='PRODUCT'" v-model="keyword" placeholder="输入商品名后点击搜索" @keyup.enter="searchTargets" />
<el-button @click="searchTargets">{{form.jumpType==='PRODUCT'?'搜索商品':'加载分类'}}</el-button>
<el-select v-model="form.jumpTargetId" filterable placeholder="选择有效目标" :loading="targetLoading" style="width:100%">
<el-option v-for="target in targets" :key="target.id" :label="target.name" :value="target.id" />
</el-select>
<el-button v-if="targetsMore" link :loading="targetLoading" @click="loadTargets(false)">加载更多</el-button>
<p class="muted">启用时目标必须存在且已启用。当前目标不可用时，请重新选择。</p>
</div>
</el-form-item>
<el-form-item label="排序">
<el-input-number v-model="form.sort" :min="0" :max="2147483647" :precision="0" />
</el-form-item>
<el-form-item label="状态">
<el-radio-group v-model="form.status">
<el-radio value="ACTIVE">启用</el-radio>
<el-radio value="DISABLED">停用</el-radio>
</el-radio-group>
</el-form-item>
</el-form>
<template #footer>
<el-button :disabled="saving||uploading" @click="visible=false">取消</el-button>
<el-button type="primary" :loading="saving" :disabled="uploading" @click="save">保存</el-button>
</template>
</el-dialog>
</div>
</template>
<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue';
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus';
import { getBanners, getBanner, createBanner, updateBanner, deleteBanner } from '../api/business';
import { getProducts, getCategories, uploadProductImage } from '../api/product';
import type { Banner, BannerRequest } from '../types/business';
const rows = ref<Banner[]>([]), status = ref(''), page = ref(1), total = ref(0), loading = ref(false), error = ref(''), visible = ref(false), saving = ref(false), uploading = ref(false), editing = ref<number | null>(null), keyword = ref(''), targets = ref<{
    id: number;
    name: string;
}[]>([]), targetLoading = ref(false), targetsMore = ref(false);
let targetPage = 1, targetSequence = 0, listSequence = 0, editSequence = 0;
const empty = (): BannerRequest => ({
    title: '', imageUrl: '', jumpType: 'NONE', jumpTargetId: null, sort: 0, status: 'DISABLED'
}), form = reactive<BannerRequest>(empty()), jumpLabels = { NONE: '不跳转', PRODUCT: '商品', CATEGORY: '分类' };
function report(e: unknown) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败');
}
async function load() {
    const seq = ++listSequence;
    loading.value = true;
    try {
        const result = await getBanners({ status: status.value, page: page.value, pageSize: 20 });
        if (seq === listSequence) {
            rows.value = result.items;
            total.value = result.total;
            error.value = '';
        }
    }
    catch (e) {
        if (seq === listSequence)
            error.value = e instanceof Error ? e.message : '加载失败';
    }
    finally {
        if (seq === listSequence)
            loading.value = false;
    }
}
async function open(id?: number) {
    const seq = ++editSequence;
    try {
        const value = id ? await getBanner(id) : null;
        if (seq !== editSequence)
            return;
        editing.value = id ?? null;
        Object.assign(form, value || empty());
        targets.value = value?.jumpTargetId ? [{ id: value.jumpTargetId, name: value.targetName || '原目标（不可用）' }] : [];
        keyword.value = '';
        targetsMore.value = false;
        targetSequence++;
        visible.value = true;
        if (form.jumpType !== 'NONE')
            void loadTargets(true);
    }
    catch (e) {
        report(e);
    }
}
function beforeClose(done: () => void) {
    if (!saving.value && !uploading.value)
        done();
}
function changeType() {
    form.jumpTargetId = null;
    targets.value = [];
    keyword.value = '';
    void loadTargets(true);
}
function searchTargets() {
    void loadTargets(true);
}
async function loadTargets(reset: boolean) {
    const seq = ++targetSequence, type = form.jumpType;
    if (type === 'NONE') {
        targets.value = [];
        targetsMore.value = false;
        return;
    }
    const next = reset ? 1 : targetPage + 1;
    targetLoading.value = true;
    try {
        const result = type === 'PRODUCT' ? await getProducts({
            keyword: keyword.value, status: 'ACTIVE', page: next, pageSize: 20
        }) : await getCategories({ status: 'ACTIVE', page: next, pageSize: 20 });
        if (seq !== targetSequence)
            return;
        const selected = targets.value.filter(x => x.id === form.jumpTargetId);
        const list = reset ? selected : targets.value;
        targets.value = Array.from(new Map([...list, ...result.items.map(x => ({ id: x.id, name: x.name }))].map(x => [x.id, x])).values());
        targetPage = next;
        targetsMore.value = next * 20 < result.total;
    }
    catch (e) {
        if (seq === targetSequence)
            report(e);
    }
    finally {
        if (seq === targetSequence)
            targetLoading.value = false;
    }
}
async function upload(options: UploadRequestOptions) {
    uploading.value = true;
    try {
        if (options.file.size > 5 * 1024 * 1024)
            throw new Error('图片不能超过5 MB');
        if (!['image/jpeg', 'image/png', 'image/webp'].includes(options.file.type))
            throw new Error('仅支持 JPG、PNG、WebP');
        form.imageUrl = (await uploadProductImage(options.file)).url;
        ElMessage.success('上传成功');
    }
    catch (e) {
        report(e);
    }
    finally {
        uploading.value = false;
    }
}
function payload(): BannerRequest {
    if (!form.title.trim() || !form.imageUrl)
        throw new Error('请填写标题并上传图片');
    if (form.jumpType !== 'NONE' && !form.jumpTargetId)
        throw new Error('请选择跳转目标');
    if (!Number.isInteger(form.sort) || form.sort < 0)
        throw new Error('排序须为非负整数');
    return {
        title: form.title.trim(), imageUrl: form.imageUrl, jumpType: form.jumpType, jumpTargetId: form.jumpType === 'NONE' ? null : form.jumpTargetId, sort: form.sort, status: form.status
    };
}
async function save() {
    if (saving.value || uploading.value)
        return;
    try {
        const body = payload();
        saving.value = true;
        if (editing.value)
            await updateBanner(editing.value, body);
        else
            await createBanner(body);
        visible.value = false;
        ElMessage.success('已保存');
        await load();
    }
    catch (e) {
        report(e);
    }
    finally {
        saving.value = false;
    }
}
async function toggle(row: Banner) {
    try {
        await ElMessageBox.confirm('确认' + (row.status === 'ACTIVE' ? '停用' : '启用') + '该轮播图？', '确认操作');
        await updateBanner(row.id, {
            title: row.title, imageUrl: row.imageUrl, jumpType: row.jumpType, jumpTargetId: row.jumpTargetId, sort: row.sort, status: row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
        });
        await load();
    }
    catch (e) {
        if (e instanceof Error)
            report(e);
    }
}
async function remove(row: Banner) {
    try {
        await ElMessageBox.confirm('确认删除“' + row.title + '”？', '删除轮播图', { type: 'warning' });
        await deleteBanner(row.id);
        if (rows.value.length === 1 && page.value > 1)
            page.value--;
        await load();
    }
    catch (e) {
        if (e instanceof Error)
            report(e);
    }
}
onMounted(load);
onUnmounted(() => {
    listSequence++;
    targetSequence++;
    editSequence++;
});
</script>
