package com.fishmall.payment;
import com.fishmall.payment.repository.PaymentEventLog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentAudit {
    private final PaymentEventLog log;
    public PaymentAudit(PaymentEventLog log) { this.log = log; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String key, String no, String result, String encrypted) { log.save(key, no, result, encrypted); }
}
