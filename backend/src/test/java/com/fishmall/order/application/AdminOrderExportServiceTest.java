package com.fishmall.order.application;

import com.fishmall.admin.repository.AdminOperationLogMapper;
import com.fishmall.admin.security.AdminPrincipal;
import com.fishmall.common.BusinessException;
import com.fishmall.common.ErrorCode;
import com.fishmall.order.application.service.AdminOrderExportService;
import com.fishmall.order.application.service.AdminOrderService;
import com.fishmall.order.repository.SalesOrderItemMapper;
import com.fishmall.order.repository.SalesOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderExportServiceTest {
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderItemMapper orderItemMapper;
    @Mock private AdminOperationLogMapper operationLogMapper;
    @Mock private AdminOrderService orderService;

    @Test
    void rejectsRangeLongerThanThirtyOneInclusiveDays() {
        AdminOrderExportService service = new AdminOrderExportService(orderMapper, orderItemMapper, operationLogMapper, orderService);

        assertThatThrownBy(() -> service.export(null, null, null, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 1), principal(), "trace"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void exportsEmptyWorkbookForValidRange() {
        when(orderMapper.selectForExport(any(), any(), any(), any(), any())).thenReturn(java.util.List.of());
        AdminOrderExportService service = new AdminOrderExportService(orderMapper, orderItemMapper, operationLogMapper, orderService);

        byte[] content = service.export(null, null, null, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), principal(), "trace");

        assertThat(content).isNotEmpty();
    }

    private AdminPrincipal principal() {
        return new AdminPrincipal(1L, "admin", 0L);
    }
}
