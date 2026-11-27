package br.com.supermercados.prices.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record RecordPurchaseRequest(
        @NotNull UUID id,
        @NotNull UUID storeId,
        @NotEmpty @Size(max = 200) List<@Valid @NotNull BoughtItem> items) {

    public RecordPurchaseRequest {
        items = List.copyOf(items);
    }

    public record BoughtItem(@NotNull UUID itemId) {}
}
