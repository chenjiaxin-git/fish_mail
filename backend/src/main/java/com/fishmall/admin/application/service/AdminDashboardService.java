package com.fishmall.admin.application.service;

import com.fishmall.admin.api.vo.DashboardResponse;
import com.fishmall.admin.repository.AdminOperationLogMapper;
import com.fishmall.admin.repository.DashboardMapper;
import com.fishmall.admin.security.AdminPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.ZoneId;

@Service
public class AdminDashboardService {
    private final DashboardMapper mapper;
    private final AdminOperationLogMapper audit;
    private final Clock clock;
    public AdminDashboardService(DashboardMapper mapper, AdminOperationLogMapper audit, Clock clock) {
        this.mapper=mapper; this.audit=audit; this.clock=clock;
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public DashboardResponse get(AdminPrincipal principal, String traceId) {
        var generated=clock.instant().atZone(ZoneId.of("Asia/Shanghai")); var day=generated.toLocalDate();
        var counts=mapper.counts(day.atStartOfDay(),day.plusDays(1).atStartOfDay());
        long amount=mapper.sales(day.atStartOfDay(),day.plusDays(1).atStartOfDay()).longValueExact();
        long pending=Math.addExact(Math.addExact(counts.quote(),counts.delivery()),Math.addExact(counts.completion(),counts.refund()));
        audit.insert(principal.adminId(),principal.username(),"DASHBOARD","READ",null,"SUCCESS",null,traceId);
        return new DashboardResponse(day,counts.today(),amount,pending,counts.quote(),counts.delivery(),counts.completion(),counts.refund(),generated.toOffsetDateTime());
    }
}
