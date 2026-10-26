package br.com.supermercados.prices.purchase;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PurchaseItemRepository extends JpaRepository<PurchaseItem, UUID> {

    List<PurchaseItem> findByPurchaseIdInOrderByProductName(Collection<UUID> purchaseIds);
}
