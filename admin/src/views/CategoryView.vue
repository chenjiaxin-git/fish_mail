<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <strong>商品分类</strong>
        <el-button type="primary" @click="openCreate">新增分类</el-button>
      </div>
    </template>

    <div class="filters">
      <el-select v-model="status" class="filter-item" clearable placeholder="全部状态" @change="loadCategories">
        <el-option label="启用" value="ACTIVE" />
        <el-option label="停用" value="DISABLED" />
      </el-select>
      <el-button @click="loadCategories">刷新</el-button>
    </div>

    <el-table :data="categories" v-loading="loading" border>
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="sort" label="排序" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="removeCategory(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pagination"
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      @change="loadCategories"
    />
  </el-card>

  <el-dialog v-model="dialogVisible" :title="editingId ? '编辑分类' : '新增分类'" width="480px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="名称" prop="name">
        <el-input v-model.trim="form.name" maxlength="50" placeholder="请输入分类名称" />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="form.sort" :min="0" :max="9999" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio value="ACTIVE">启用</el-radio>
          <el-radio value="DISABLED">停用</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveCategory">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createCategory, deleteCategory, getCategories, updateCategory } from '../api/product'
import { formatDateTime } from '../utils/format'
import type { Category, CategoryRequest } from '../types/product'

const categories = ref<Category[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const status = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const formRef = ref<FormInstance>()
const form = reactive<CategoryRequest>({ name: '', sort: 0, status: 'ACTIVE' })
const rules: FormRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

onMounted(loadCategories)

async function loadCategories(): Promise<void> {
  loading.value = true
  try {
    const result = await getCategories({ status: status.value || undefined, page: page.value, pageSize: pageSize.value })
    categories.value = result.items
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  form.name = ''
  form.sort = 0
  form.status = 'ACTIVE'
  dialogVisible.value = true
}

function openEdit(row: Category): void {
  editingId.value = row.id
  form.name = row.name
  form.sort = row.sort
  form.status = row.status
  dialogVisible.value = true
}

async function saveCategory(): Promise<void> {
  if (!formRef.value || saving.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) await updateCategory(editingId.value, form)
    else await createCategory(form)
    dialogVisible.value = false
    ElMessage.success('保存成功')
    await loadCategories()
  } finally {
    saving.value = false
  }
}

async function removeCategory(row: Category): Promise<void> {
  await ElMessageBox.confirm(`确认删除分类“${row.name}”吗？`, '删除确认', { type: 'warning' })
  await deleteCategory(row.id)
  ElMessage.success('删除成功')
  await loadCategories()
}
</script>

<style scoped>
.card-header, .filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.filters {
  justify-content: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.filter-item {
  width: 160px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
