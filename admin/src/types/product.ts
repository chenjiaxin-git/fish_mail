export interface PageResult<T> {
  total: number
  page: number
  pageSize: number
  items: T[]
}

export interface Category {
  id: number
  name: string
  sort: number
  status: 'ACTIVE' | 'DISABLED'
  createdAt: string
  updatedAt: string
}

export interface CategoryRequest {
  name: string
  sort: number
  status: 'ACTIVE' | 'DISABLED'
}

export interface ProductSpec {
  id: number | null
  productId?: number
  name: string
  quantityKind: 'PIECE' | 'WEIGHT'
  unit: string
  priceCent: number
  minQuantity: number | string
  stepQuantity: number | string
  physicalStockQty: number | string
  reservedQty?: number | string
  availableQty?: number | string
  warningQty: number | string
  status: 'ACTIVE' | 'DISABLED'
}

export interface ProductListItem {
  id: number
  categoryId: number
  categoryName: string
  name: string
  subtitle: string
  mainImageUrl: string
  status: 'ACTIVE' | 'OFF_SHELF'
  specCount: number
  minPriceCent: number | null
  maxPriceCent: number | null
  totalPhysicalStockQty: number | string
  totalReservedQty: number | string
  totalAvailableQty: number | string
  createdAt: string
  updatedAt: string
}

export interface ProductDetail {
  id: number
  categoryId: number
  categoryName: string
  name: string
  subtitle: string
  description: string
  mainImageUrl: string
  status: 'ACTIVE' | 'OFF_SHELF'
  specs: ProductSpec[]
  createdAt: string
  updatedAt: string
}

export interface ProductSpecRequest {
  id: number | null
  name: string
  quantityKind: 'PIECE' | 'WEIGHT'
  unit: string
  priceCent: number
  minQuantity: number | string
  stepQuantity: number | string
  physicalStockQty: number | string
  warningQty: number | string
  status: 'ACTIVE' | 'DISABLED'
}

export interface ProductRequest {
  categoryId: number
  name: string
  subtitle: string
  description: string
  mainImageUrl: string
  status: 'ACTIVE' | 'OFF_SHELF'
  specs: ProductSpecRequest[]
}

export interface StockAdjustRequest {
  targetQuantity: number | string
  reason: string
}

export interface StockLedger {
  id: number
  changeType: string
  quantity: number | string
  physicalBefore: number | string
  physicalAfter: number | string
  reservedBefore: number | string
  reservedAfter: number | string
  reason: string
  bizKey: string
  createdAt: string
}

export interface StockAdjustResult {
  productSpecId: number
  beforeQuantity: number | string
  afterQuantity: number | string
  reservedQuantity: number | string
  availableQuantity: number | string
  ledger: StockLedger
}

export interface FileUploadResult {
  url: string
  fileName: string
  sizeBytes: number
}
