package br.com.supermercados.prices.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * "Terminei a compra": the list items that went into the cart at {@code storeId}.
 *
 * @param id chosen by the client, so sending the same purchase again (a weak signal inside the market) is safe
 */
public record RecordPurchaseRequest(
        @NotNull UUID id,
        @NotNull UUID storeId,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull BoughtItem> items) {

    public RecordPurchaseRequest {
        items = List.copyOf(items);
    }

    /** @param shelfPrice the price the person saw on the shelf, when it differed from Gomo's */
    public record BoughtItem(
            @NotNull UUID itemId,
            @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal shelfPrice) {
    }
}
