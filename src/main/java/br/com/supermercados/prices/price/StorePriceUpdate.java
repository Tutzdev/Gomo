package br.com.supermercados.prices.price;

import java.time.Instant;
import java.util.UUID;

public record StorePriceUpdate(UUID storeId, Instant collectedAt) {}
