package br.com.supermercados.prices.whatsapp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

record PendingAlert(
        UUID notificationId,
        WhatsAppNumber number,
        String productName,
        String storeName,
        BigDecimal price) {

    String priceLabel() {
        return "R$ " + price.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
