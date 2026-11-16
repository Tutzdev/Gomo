package br.com.supermercados.prices.shoppinglist;

import java.time.Instant;
import java.util.UUID;

public record ShoppingListMemberResponse(UUID userId, String name, Instant joinedAt) {}
