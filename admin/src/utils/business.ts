import type { OrderFilters, OrderStatus, RefundStatus, QuoteItem } from '../types/business';
export const orderStatuses: Record<OrderStatus, string> = {
    CREATED: '待报价', QUOTED: '待用户确认', PENDING_PAYMENT: '待支付', PAID_WAIT_DELIVERY: '待配送', DELIVERING: '配送中', COMPLETED: '已完成', CANCELLED: '已取消', CLOSED: '已关闭'
};
export const refundStatuses: Record<RefundStatus, string> = { NONE: '无需退款', REQUIRED: '待登记退款', MANUAL_REFUNDED: '已登记人工退款' };
export function allowedActions(status: OrderStatus, refund: RefundStatus) {
    return {
        quote: ['CREATED', 'QUOTED'].includes(status), deliver: status === 'PAID_WAIT_DELIVERY', complete: status === 'DELIVERING', cancel: ['CREATED', 'QUOTED', 'PENDING_PAYMENT', 'PAID_WAIT_DELIVERY'].includes(status), refund: status === 'CANCELLED' && refund === 'REQUIRED'
    };
}
export function yuanToCent(input: string): number {
    const s = input.trim();
    if (!/^\d+(\.\d{1,2})?$/.test(s))
        throw new Error('金额须为非负数字，最多两位小数');
    const [a, b = ''] = s.split('.');
    const n = BigInt(a) * 100n + BigInt(b.padEnd(2, '0'));
    if (n > BigInt(Number.MAX_SAFE_INTEGER))
        throw new Error('金额超出范围');
    return Number(n);
}
export function centToInput(n: number): string {
    if (!Number.isSafeInteger(n) || n < 0)
        throw new Error('金额超出范围');
    const v = BigInt(n);
    return String(v / 100n) + '.' + String(v % 100n).padStart(2, '0');
}
export function validateQuantity(input: string, kind: string): string {
    const s = input.trim();
    if (!/^\d{1,9}(\.\d{1,3})?$/.test(s))
        throw new Error('数量最多九位整数、三位小数');
    const [a, b = ''] = s.split('.');
    if (BigInt(a) * 1000n + BigInt(b.padEnd(3, '0')) <= 0n)
        throw new Error('实称数量必须大于零');
    if (kind === 'PIECE' && BigInt(b.padEnd(3, '0')) !== 0n)
        throw new Error('按件商品数量必须为整数');
    return s;
}
export function referenceAmount(qty: string, price: number): string {
    validateQuantity(qty, 'WEIGHT');
    const [a, b = ''] = qty.trim().split('.');
    const n = ((BigInt(a) * 1000n + BigInt(b.padEnd(3, '0'))) * BigInt(price) + 500n) / 1000n;
    if (n > BigInt(Number.MAX_SAFE_INTEGER))
        throw new Error('金额超出范围');
    return centToInput(Number(n));
}
export function sumCent(values: number[]): number {
    const n = values.reduce((a, b) => a + BigInt(b), 0n);
    if (n > BigInt(Number.MAX_SAFE_INTEGER))
        throw new Error('合计金额超出范围');
    return Number(n);
}
export function parseRevisions(value: string): QuoteItem[] | null {
    try {
        const v: unknown = JSON.parse(value);
        if (!Array.isArray(v) || !v.every(x => x && Number.isSafeInteger(x.orderItemId) && Number.isSafeInteger(x.finalAmountCent) && (typeof x.actualQuantity === 'string' || typeof x.actualQuantity === 'number')))
            return null;
        return v.map(x => ({ ...x, actualQuantity: String(x.actualQuantity) }));
    }
    catch {
        return null;
    }
}
export function parseOrderFilters(q: Record<string, unknown>): OrderFilters {
    const s = (k: string) => typeof q[k] === 'string' ? q[k] as string : '';
    const positive = (k: string, d: number, max: number) => /^\d+$/.test(s(k)) && Number(s(k)) > 0 && Number(s(k)) <= max ? Number(s(k)) : d;
    return {
        status: Object.prototype.hasOwnProperty.call(orderStatuses, s('status')) ? s('status') as OrderStatus : '', refundStatus: Object.prototype.hasOwnProperty.call(refundStatuses, s('refundStatus')) ? s('refundStatus') as RefundStatus : '', orderNo: s('orderNo').slice(0, 40), phone: s('phone').slice(0, 32), startDate: s('startDate'), endDate: s('endDate'), page: positive('page', 1, 10000000), pageSize: positive('pageSize', 20, 100)
    };
}
export function validateExportDates(start: string, end: string): void {
    const day = (s: string) => {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(s))
            throw new Error('请选择完整的导出日期');
        const n = Date.parse(s + 'T00:00:00Z');
        if (!Number.isFinite(n) || new Date(n).toISOString().slice(0, 10) !== s)
            throw new Error('日期无效');
        return n;
    };
    const a = day(start), b = day(end);
    if (b < a || b - a > 30 * 86400000)
        throw new Error('导出日期须正序且最多包含31天');
}

const eventLabels: Record<string, string> = {
    INIT: '待支付', SUCCESS: '支付成功', FAILED: '支付失败', NOTPAY: '尚未付款', USERPAYING: '付款处理中',
    CLOSED: '已关闭', REVOKED: '已撤销', PAYERROR: '支付失败', REFUND: '已转退款',
    ADMIN: '商家', USER: '用户', SYSTEM: '系统', PAYMENT: '支付系统',
    MINI_PROGRAM: '小程序', WECHAT_PAY: '微信支付', TIMEOUT_JOB: '超时关闭任务'
};
export function eventLabel(value: string | null | undefined): string {
    return value ? eventLabels[value] || value : '—';
}
