<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <strong>商品管理</strong>
        <el-button type="primary" @click="openCreate">新增商品</el-button>
      </div>
    </template>

    <div class="filters">
      <el-input v-model.trim="keyword" class="keyword" clearable placeholder="商品名称" @keyup.enter="search" />
      <el-select v-model="categoryId" class="filter-item" clearable placeholder="全部分类">
        <el-option v-for="category in categoryOptions" :key="category.id" :label="category.name" :value="category.id" />
      </el-select>
      <el-select v-model="status" class="filter-item" clearable placeholder="全部状态">
        <el-option label="上架" value="ACTIVE" />
        <el-option label="下架" value="OFF_SHELF" />
      </el-select>
      <el-button type="primary" plain @click="search">搜索</el-button>
      <el-button @click="resetSearch">重置</el-button>
    </div>

    <el-table :data="products" v-loading="loading" border>
      <el-table-column label="主图" width="90">
        <template #default="{ row }">
          <el-image v-if="row.mainImageUrl" :src="row.mainImageUrl" class="product-image" fit="cover" />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="categoryName" label="分类" width="120" />
      <el-table-column label="规格摘要" width="120">
        <template #default="{ row }">{{ row.specCount }} 个规格</template>
      </el-table-column>
      <el-table-column label="价格范围" width="150">
        <template #default="{ row }">{{ priceRange(row) }}</template>
      </el-table-column>
      <el-table-column label="库存摘要" width="170">
        <template #default="{ row }">可用 {{ formatQuantity(row.totalAvailableQty) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '上架' : '下架' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row.id)">编辑</el-button>
          <el-button link type="danger" @click="removeProduct(row)">删除</el-button>
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
      @change="loadProducts"
    />
  </el-card>

  <el-drawer v-model="dialogVisible" :title="editingId ? '编辑商品' : '新增商品'" size="86%" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="商品名称" prop="name">
        <el-input v-model.trim="form.name" maxlength="100" placeholder="请输入商品名称" />
      </el-form-item>
      <el-form-item label="分类" prop="categoryId">
        <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 280px">
          <el-option v-for="category in categoryOptions" :key="category.id" :label="category.name" :value="category.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="副标题">
        <el-input v-model.trim="form.subtitle" maxlength="150" placeholder="请输入副标题" />
      </el-form-item>
      <el-form-item label="商品描述">
        <el-input v-model="form.description" type="textarea" :rows="4" maxlength="5000" placeholder="请输入商品描述" />
      </el-form-item>
      <el-form-item label="商品主图">
        <div class="image-upload">
          <el-image v-if="form.mainImageUrl" :src="form.mainImageUrl" class="preview-image" fit="cover" />
          <el-upload :show-file-list="false" accept="image/jpeg,image/png,image/webp" :http-request="uploadImage">
            <el-button :loading="uploading">上传图片</el-button>
          </el-upload>
          <el-button v-if="form.mainImageUrl" link type="danger" @click="form.mainImageUrl = ''">移除</el-button>
        </div>
      </el-form-item>
      <el-form-item label="商品状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio value="ACTIVE">上架</el-radio>
          <el-radio value="OFF_SHELF">下架</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="商品规格" required>
        <div class="spec-toolbar">
          <el-button @click="addSpec">新增规格</el-button>
          <span class="hint">已有库存请通过“调整库存”修改</span>
        </div>
        <el-table :data="specRows" border class="spec-table">
          <el-table-column label="规格名称" min-width="130">
            <template #default="{ row }"><el-input v-model.trim="row.name" maxlength="50" /></template>
          </el-table-column>
          <el-table-column label="计价方式" width="120">
            <template #default="{ row }">
              <el-select v-model="row.quantityKind" :disabled="isLocked(row)">
                <el-option label="按件" value="PIECE" />
                <el-option label="按重量" value="WEIGHT" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="单位" width="100">
            <template #default="{ row }"><el-input v-model.trim="row.unit" maxlength="20" :disabled="isLocked(row)" /></template>
          </el-table-column>
          <el-table-column label="单价(元)" width="130">
            <template #default="{ row }"><el-input-number v-model="row.priceCent" :min="1" :precision="2" controls-position="right" /></template>
          </el-table-column>
          <el-table-column label="最小购买量" width="130">
            <template #default="{ row }"><el-input-number v-model="row.minQuantity" :min="0.001" :precision="3" controls-position="right" :disabled="isLocked(row)" /></template>
          </el-table-column>
          <el-table-column label="步进值" width="130">
            <template #default="{ row }"><el-input-number v-model="row.stepQuantity" :min="0.001" :precision="3" controls-position="right" :disabled="isLocked(row)" /></template>
          </el-table-column>
          <el-table-column label="物理库存" width="150">
            <template #default="{ row }">
              <el-input-number v-if="row.id === null" v-model="row.physicalStockQty" :min="0" :precision="3" controls-position="right" />
              <span v-else>{{ formatQuantity(row.physicalStockQty) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预警量" width="130">
            <template #default="{ row }"><el-input-number v-model="row.warningQty" :min="0" :precision="3" controls-position="right" /></template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-select v-model="row.status">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="停用" value="DISABLED" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.id !== null" link type="primary" @click="openStockAdjust(row)">调整库存</el-button>
              <el-button link type="danger" :disabled="isLocked(row)" @click="removeSpec(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveProduct">保存</el-button>
    </template>
  </el-drawer>

  <el-dialog v-model="stockDialogVisible" title="调整规格库存" width="440px">
    <el-form label-width="90px">
      <el-form-item label="规格">{{ stockSpec?.name }}</el-form-item>
      <el-form-item label="当前库存">{{ formatQuantity(stockSpec?.physicalStockQty) }}</el-form-item>
      <el-form-item label="已预占">{{ formatQuantity(stockSpec?.reservedQty) }}</el-form-item>
      <el-form-item label="调整后库存">
        <el-input-number v-model="stockForm.targetQuantity" :min="0" :precision="3" controls-position="right" />
      </el-form-item>
      <el-form-item label="调整原因">
        <el-input v-model.trim="stockForm.reason" type="textarea" :rows="3" maxlength="200" placeholder="请输入调整原因" />
      </el-form-item>
      <el-form-item label="最近流水">
        <el-table :data="stockLedgerRows" size="small" border max-height="180">
          <el-table-column prop="changeType" label="类型" width="110" />
          <el-table-column label="变更" width="100">
            <template #default="{ row }">{{ formatQuantity(row.quantity) }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="120" />
        </el-table>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="stockDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="stockSaving" @click="saveStock">确认调整</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import { adjustStock, createProduct, deleteProduct, getProduct, getCategories, getProducts, getStockLedger, updateProduct, uploadProductImage } from '../api/product'
import { formatDateTime, formatMoneyCent, formatQuantity } from '../utils/format'
import type { Category, ProductDetail, ProductListItem, ProductRequest, ProductSpec, ProductSpecRequest, StockLedger } from '../types/product'

interface SpecDraft {
  id: number | null
  name: string
  quantityKind: 'PIECE' | 'WEIGHT'
  unit: string
  priceCent: number
  minQuantity: number
  stepQuantity: number
  physicalStockQty: number
  warningQty: number
  status: 'ACTIVE' | 'DISABLED'
  reservedQty: number
  originalPhysicalStockQty: number
}

const products = ref<ProductListItem[]>([])
const categoryOptions = ref<Category[]>([])
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const keyword = ref('')
const categoryId = ref<number | undefined>()
const status = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const formRef = ref<FormInstance>()
const specRows = ref<SpecDraft[]>([])
const stockDialogVisible = ref(false)
const stockSaving = ref(false)
const stockSpec = ref<SpecDraft | null>(null)
const stockForm = reactive({ targetQuantity: 0, reason: '' })
const stockLedgerRows = ref<StockLedger[]>([])
const form = reactive<{
  categoryId: number | undefined
  name: string
  subtitle: string
  description: string
  mainImageUrl: string
  status: 'ACTIVE' | 'OFF_SHELF'
}>({ categoryId: undefined, name: '', subtitle: '', description: '', mainImageUrl: '', status: 'ACTIVE' })
const rules: FormRules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  status: [{ required: true, message: '请选择商品状态', trigger: 'change' }]
}

onMounted(async () => {
  await Promise.all([loadCategories(), loadProducts()])
})

async function loadCategories(): Promise<void> {
  const result = await getCategories({ page: 1, pageSize: 100 })
  categoryOptions.value = result.items
}

async function loadProducts(): Promise<void> {
  loading.value = true
  try {
    const result = await getProducts({
      keyword: keyword.value || undefined,
      categoryId: categoryId.value,
      status: status.value || undefined,
      page: page.value,
      pageSize: pageSize.value
    })
    products.value = result.items
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  loadProducts()
}

function resetSearch(): void {
  keyword.value = ''
  categoryId.value = undefined
  status.value = ''
  search()
}

function openCreate(): void {
  editingId.value = null
  form.categoryId = categoryOptions.value[0]?.id
  form.name = ''
  form.subtitle = ''
  form.description = ''
  form.mainImageUrl = ''
  form.status = 'ACTIVE'
  specRows.value = [emptySpec()]
  dialogVisible.value = true
}

async function openEdit(id: number): Promise<void> {
  const detail: ProductDetail = await getProduct(id)
  editingId.value = detail.id
  form.categoryId = detail.categoryId
  form.name = detail.name
  form.subtitle = detail.subtitle
  form.description = detail.description
  form.mainImageUrl = detail.mainImageUrl
  form.status = detail.status
  specRows.value = detail.specs.map((spec) => ({
    id: spec.id,
    name: spec.name,
    quantityKind: spec.quantityKind,
    unit: spec.unit,
    priceCent: Number(spec.priceCent) / 100,
    minQuantity: Number(spec.minQuantity),
    stepQuantity: Number(spec.stepQuantity),
    physicalStockQty: Number(spec.physicalStockQty),
    warningQty: Number(spec.warningQty),
    status: spec.status,
    reservedQty: Number(spec.reservedQty ?? 0),
    originalPhysicalStockQty: Number(spec.physicalStockQty)
  }))
  dialogVisible.value = true
}

function emptySpec(): SpecDraft {
  return {
    id: null,
    name: '',
    quantityKind: 'WEIGHT',
    unit: 'kg',
    priceCent: 0,
    minQuantity: 1,
    stepQuantity: 0.5,
    physicalStockQty: 0,
    warningQty: 0,
    status: 'ACTIVE',
    reservedQty: 0,
    originalPhysicalStockQty: 0
  }
}

function addSpec(): void {
  if (specRows.value.length >= 20) {
    ElMessage.warning('规格不能超过20个')
    return
  }
  specRows.value.push(emptySpec())
}

function removeSpec(row: SpecDraft): void {
  if (isLocked(row)) return
  specRows.value = specRows.value.filter((item) => item !== row)
}

function isLocked(row: SpecDraft): boolean {
  return row.id !== null && row.reservedQty > 0
}

async function uploadImage(options: UploadRequestOptions): Promise<void> {
  uploading.value = true
  try {
    const result = await uploadProductImage(options.file as File)
    form.mainImageUrl = result.url
    ElMessage.success('图片上传成功')
  } finally {
    uploading.value = false
  }
}

async function saveProduct(): Promise<void> {
  if (!formRef.value || saving.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (specRows.value.length === 0) {
    ElMessage.warning('至少需要一个规格')
    return
  }
  saving.value = true
  try {
    const payload: ProductRequest = {
      categoryId: form.categoryId!,
      name: form.name,
      subtitle: form.subtitle,
      description: form.description,
      mainImageUrl: form.mainImageUrl,
      status: form.status,
      specs: specRows.value.map((row): ProductSpecRequest => ({
        id: row.id,
        name: row.name,
        quantityKind: row.quantityKind,
        unit: row.unit,
        priceCent: Math.round(row.priceCent * 100),
        minQuantity: row.minQuantity,
        stepQuantity: row.stepQuantity,
        physicalStockQty: row.physicalStockQty,
        warningQty: row.warningQty,
        status: row.status
      }))
    }
    if (editingId.value) await updateProduct(editingId.value, payload)
    else await createProduct(payload)
    dialogVisible.value = false
    ElMessage.success('保存成功')
    await loadProducts()
  } finally {
    saving.value = false
  }
}

async function removeProduct(row: ProductListItem): Promise<void> {
  await ElMessageBox.confirm(`确认删除商品“${row.name}”吗？`, '删除确认', { type: 'warning' })
  await deleteProduct(row.id)
  ElMessage.success('删除成功')
  await loadProducts()
}

function openStockAdjust(row: SpecDraft): void {
  stockSpec.value = row
  stockForm.targetQuantity = row.physicalStockQty
  stockForm.reason = ''
  stockLedgerRows.value = []
  stockDialogVisible.value = true
  if (editingId.value && row.id !== null) {
    getStockLedger(editingId.value, row.id).then((rows) => { stockLedgerRows.value = rows }).catch(() => undefined)
  }
}

async function saveStock(): Promise<void> {
  if (!stockSpec.value || stockSaving.value || !editingId.value) return
  if (!stockForm.reason.trim()) {
    ElMessage.warning('请输入调整原因')
    return
  }
  stockSaving.value = true
  try {
    const result = await adjustStock(editingId.value, stockSpec.value.id!, {
      targetQuantity: stockForm.targetQuantity,
      reason: stockForm.reason
    })
    stockSpec.value.physicalStockQty = Number(result.afterQuantity)
    stockSpec.value.originalPhysicalStockQty = Number(result.afterQuantity)
    stockSpec.value.reservedQty = Number(result.reservedQuantity)
    stockLedgerRows.value = [result.ledger, ...stockLedgerRows.value].slice(0, 50)
    stockDialogVisible.value = false
    ElMessage.success(`库存已调整，变更数量 ${formatQuantity(result.ledger.quantity)}`)
    await loadProducts()
  } finally {
    stockSaving.value = false
  }
}

function priceRange(row: ProductListItem): string {
  if (row.minPriceCent === null || row.maxPriceCent === null) return '-'
  return row.minPriceCent === row.maxPriceCent
    ? formatMoneyCent(row.minPriceCent)
    : formatMoneyCent(row.minPriceCent) + ' - ' + formatMoneyCent(row.maxPriceCent)
}
</script>

<style scoped>
.card-header, .filters, .spec-toolbar, .image-upload {
  display: flex;
  align-items: center;
}

.card-header, .spec-toolbar {
  justify-content: space-between;
}

.filters {
  gap: 12px;
  margin-bottom: 16px;
}

.keyword {
  width: 220px;
}

.filter-item {
  width: 160px;
}

.product-image {
  width: 56px;
  height: 56px;
}

.preview-image {
  width: 120px;
  height: 120px;
  margin-right: 12px;
  border: 1px solid #dcdfe6;
}

.image-upload {
  gap: 12px;
}

.spec-toolbar {
  width: 100%;
  margin-bottom: 8px;
}

.hint {
  color: #909399;
  font-size: 12px;
}

.spec-table {
  width: 100%;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
