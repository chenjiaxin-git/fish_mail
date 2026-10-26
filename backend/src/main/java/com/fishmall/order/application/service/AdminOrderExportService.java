package com.fishmall.order.application.service;

import com.fishmall.admin.repository.AdminOperationLogMapper;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.api.vo.AddressSnapshotResponse;
import com.fishmall.order.domain.SalesOrder;
import com.fishmall.order.repository.SalesOrderItemMapper;
import com.fishmall.order.repository.SalesOrderMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AdminOrderExportService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] HEADERS = {
            "订单号", "创建时间", "状态", "联系人", "手机号", "收货地址", "预估金额（分）", "最终金额（分）",
            "实称明细", "退款状态", "退款凭证"
    };

    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final AdminOperationLogMapper operationLogMapper;
    private final AdminOrderService orderService;

    public AdminOrderExportService(SalesOrderMapper orderMapper,
                                   SalesOrderItemMapper orderItemMapper,
                                   AdminOperationLogMapper operationLogMapper,
                                   AdminOrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.operationLogMapper = operationLogMapper;
        this.orderService = orderService;
    }

    @Transactional
    public byte[] export(String status, String orderNo, String phone,
                         LocalDate startDate, LocalDate endDate,
                         AdminPrincipal principal, String traceId) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出日期范围不能为空");
        }
        if (startDate.isAfter(endDate) || startDate.plusDays(30).isBefore(endDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出日期范围最大31天");
        }
        List<SalesOrder> orders = orderMapper.selectForExport(status, normalize(orderNo), normalize(phone),
                startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("订单导出");
            writeHeader(sheet);
            int rowIndex = 1;
            for (SalesOrder order : orders) {
                writeOrder(sheet.createRow(rowIndex++), order);
            }
            workbook.write(output);
            workbook.dispose();
            operationLogMapper.insert(principal.adminId(), principal.username(), "ORDER", "EXPORT",
                    null, "SUCCESS", "导出" + orders.size() + "条订单", traceId);
            return output.toByteArray();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private void writeHeader(Sheet sheet) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            row.createCell(i).setCellValue(HEADERS[i]);
        }
    }

    private void writeOrder(Row row, SalesOrder order) {
        AddressSnapshotResponse address = orderService.toAddressResponse(order.getAddressSnapshot());
        String itemSummary = orderItemMapper.findByOrderId(order.getId()).stream()
                .map(item -> item.getProductName() + "/" + item.getSpecName() + ":" + item.getActualQuantity() + item.getUnit())
                .reduce((left, right) -> left + "；" + right).orElse("");
        Cell[] cells = new Cell[HEADERS.length];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = row.createCell(i);
        }
        cells[0].setCellValue(order.getOrderNo());
        cells[1].setCellValue(order.getCreatedAt() == null ? "" : DATE_TIME_FORMATTER.format(order.getCreatedAt()));
        cells[2].setCellValue(order.getStatus());
        cells[3].setCellValue(value(address.contactName()));
        cells[4].setCellValue(value(address.phone()));
        cells[5].setCellValue(value(address.detailAddress()));
        cells[6].setCellValue(order.getEstimatedAmountCent() == null ? 0 : order.getEstimatedAmountCent());
        cells[7].setCellValue(order.getFinalAmountCent() == null ? 0 : order.getFinalAmountCent());
        cells[8].setCellValue(itemSummary);
        cells[9].setCellValue(value(order.getRefundStatus()));
        cells[10].setCellValue(value(order.getRefundReference()));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
