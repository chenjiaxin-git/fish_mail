import { deleteData, getData, postData, putData, uploadFile } from './http'
import type {
  Category,
  CategoryRequest,
  FileUploadResult,
  PageResult,
  ProductDetail,
  ProductListItem,
  ProductRequest,
  StockAdjustRequest,
  StockAdjustResult,
  StockLedger
} from '../types/product'

export function getCategories(params: { status?: string; page: number; pageSize: number }): Promise<PageResult<Category>> {
  return getData('/admin/categories?' + new URLSearchParams({
    page: String(params.page),
    pageSize: String(params.pageSize),
    ...(params.status ? { status: params.status } : {})
  }))
}

export function createCategory(data: CategoryRequest): Promise<Category> {
  return postData('/admin/categories', data)
}

export function updateCategory(id: number, data: CategoryRequest): Promise<Category> {
  return putData('/admin/categories/' + id, data)
}

export function deleteCategory(id: number): Promise<void> {
  return deleteData('/admin/categories/' + id)
}

export function getProducts(params: {
  keyword?: string
  categoryId?: number
  status?: string
  page: number
  pageSize: number
}): Promise<PageResult<ProductListItem>> {
  const search = new URLSearchParams({ page: String(params.page), pageSize: String(params.pageSize) })
  if (params.keyword) search.set('keyword', params.keyword)
  if (params.categoryId) search.set('categoryId', String(params.categoryId))
  if (params.status) search.set('status', params.status)
  return getData('/admin/products?' + search.toString())
}

export function getProduct(id: number): Promise<ProductDetail> {
  return getData('/admin/products/' + id)
}

export function createProduct(data: ProductRequest): Promise<ProductDetail> {
  return postData('/admin/products', data)
}

export function updateProduct(id: number, data: ProductRequest): Promise<ProductDetail> {
  return putData('/admin/products/' + id, data)
}

export function deleteProduct(id: number): Promise<void> {
  return deleteData('/admin/products/' + id)
}

export function adjustStock(productId: number, specId: number, data: StockAdjustRequest): Promise<StockAdjustResult> {
  return postData(`/admin/products/${productId}/specs/${specId}/stock`, data)
}

export function getStockLedger(productId: number, specId: number): Promise<StockLedger[]> {
  return getData(`/admin/products/${productId}/specs/${specId}/stock-ledger`)
}

export function uploadProductImage(file: File): Promise<FileUploadResult> {
  return uploadFile('/admin/files', file)
}
