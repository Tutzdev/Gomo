package br.com.supermercados.prices.purchase;

import java.math.BigDecimal;
import java.util.List;

/**
 * "Em outubro, o Gomo te fez economizar R$ 83": the purchases of a month against the city average.
 *
 * @param month {@code yyyy-MM}
 * @param locked on the free plan the month's total shows, the purchase by purchase breakdown is Premium
 */
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
