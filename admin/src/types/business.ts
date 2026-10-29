export type OrderStatus = 'CREATED' | 'QUOTED' | 'PENDING_PAYMENT' | 'PAID_WAIT_DELIVERY' | 'DELIVERING' | 'COMPLETED' | 'CANCELLED' | 'CLOSED';
export type RefundStatus = 'NONE' | 'REQUIRED' | 'MANUAL_REFUNDED';
export interface Dashboard {
    date: string;
    todayOrderCount: number;
    todaySalesAmountCent: number;
    pendingOrderCount: number;
    pendingQuoteCount: number;
    pendingDeliveryCount: number;
    pendingCompletionCount: number;
    pendingRefundCount: number;
    generatedAt: string;
}
export interface OrderFilters {
    status: OrderStatus | '';
    refundStatus: RefundStatus | '';
    orderNo: string;
    phone: string;
    startDate: string;
    endDate: string;
    page: number;
    pageSize: number;
}
export interface OrderListItem {
    id: number;
    orderNo: string;
    status: OrderStatus;
    contactName: string;
    phone: string;
    remark: string;
    estimatedAmountCent: number;
    finalAmountCent: number | null;
    refundStatus: RefundStatus;
    refundReference: string | null;
    createdAt: string;
    updatedAt: string;
    payDeadlineAt: string | null;
}
export interface OrderItem {
    id: number;
    productName: string;
    specName: string;
    quantityKind: 'PIECE' | 'WEIGHT';
    unit: string;
    unitPriceCent: number;
    estimatedQuantity: string | number;
    actualQuantity: string | number | null;
    estimatedAmountCent: number;
    finalAmountCent: number | null;
}
export interface QuoteItem {
    orderItemId: number;
    actualQuantity: string;
    finalAmountCent: number;
}
export interface QuoteRequest {
    quoteVersion: number;
    items: QuoteItem[];
    finalAmountCent: number;
    reason: string;
}
export interface OrderDetail extends OrderListItem {
    addressSnapshot: {
        contactName: string;
        phone: string;
        detailAddress: string;
    };
    quoteVersion: number;
    source: string;
    cancelReason: string | null;
    quotedAt: string | null;
    paidAt: string | null;
    deliveredAt: string | null;
    completedAt: string | null;
    closedAt: string | null;
    cancelledAt: string | null;
    items: OrderItem[];
    priceRevisions: {
        id: number;
        revisionNo: number;
        previousAmountCent: number;
        finalAmountCent: number;
        reason: string;
        itemsSnapshot: string;
        createdAt: string;
    }[];
    payments: {
        id: number;
        outTradeNo: string;
        transactionId: string | null;
        state: string;
        tradeState: string | null;
        amountCent: number;
        paidAt: string | null;
        createdAt: string;
    }[];
    statusLogs: {
        id: number;
        fromStatus: OrderStatus | null;
        toStatus: OrderStatus;
        operatorType: string;
        operatorId: number | null;
        reason: string;
        source: string;
        createdAt: string;
    }[];
}
export interface BannerRequest {
    title: string;
    imageUrl: string;
    jumpType: 'NONE' | 'PRODUCT' | 'CATEGORY';
    jumpTargetId: number | null;
    sort: number;
    status: 'ACTIVE' | 'DISABLED';
}
export interface Banner extends BannerRequest {
    id: number;
    targetAvailable: boolean;
    targetName: string;
    createdAt: string;
    updatedAt: string;
}
