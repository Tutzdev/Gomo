package br.com.supermercados.prices.purchase;

import java.math.BigDecimal;
import java.util.List;

public record SavingsReportResponse(
        String month,
        int purchases,
        BigDecimal totalPaid,
        BigDecimal savings,
        int comparedItems,
        boolean locked,
        List<PurchaseResponse> details) {

    public SavingsReportResponse {
        details = List.copyOf(details);
    }
}
