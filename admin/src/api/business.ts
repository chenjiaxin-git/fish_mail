import { http, getData, postData, putData, deleteData } from './http';
import type { ApiResponse } from '../types/auth';
import type { PageResult } from '../types/product';
import type { Dashboard, OrderFilters, OrderListItem, OrderDetail, QuoteRequest, Banner, BannerRequest } from '../types/business';
export function filterQuery(params: object): string {
    const q = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
        if (v !== '' && v != null)
            q.set(k, String(v));
    });
    return q.toString();
}
export async function getDashboard(silent = false): Promise<Dashboard> {
    return (await http.get<ApiResponse<Dashboard>>('/admin/dashboard', { silent })).data.data;
}
export async function getOrders(params: OrderFilters, silent = false): Promise<PageResult<OrderListItem>> {
    return (await http.get<ApiResponse<PageResult<OrderListItem>>>('/admin/orders?' + filterQuery(params), { silent })).data.data;
}
export function getOrder(id: number): Promise<OrderDetail> {
    return getData('/admin/orders/' + id);
}
export function quoteOrder(id: number, body: QuoteRequest): Promise<OrderDetail> {
    return postData('/admin/orders/' + id + '/quote', body);
}
export async function orderAction(id: number, action: 'deliver' | 'complete' | 'cancel' | 'refund-confirmed', data?: object): Promise<OrderDetail> {
    return (await http.post<ApiResponse<OrderDetail>>('/admin/orders/' + id + '/' + action, data, { timeout: 90000 })).data.data;
}
export async function exportOrders(params: Omit<OrderFilters, 'page' | 'pageSize'>): Promise<Blob> {
    return (await http.get<Blob>('/admin/orders/export?' + filterQuery(params), { responseType: 'blob', timeout: 60000 })).data;
}
export function getBanners(params: object): Promise<PageResult<Banner>> {
    return getData('/admin/banners?' + filterQuery(params));
}
export function getBanner(id: number): Promise<Banner> {
    return getData('/admin/banners/' + id);
}
export function createBanner(body: BannerRequest): Promise<Banner> {
    return postData('/admin/banners', body);
}
export function updateBanner(id: number, body: BannerRequest): Promise<Banner> {
    return putData('/admin/banners/' + id, body);
}
export function deleteBanner(id: number): Promise<void> {
    return deleteData('/admin/banners/' + id);
}
