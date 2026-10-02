package br.com.supermercados.prices.price;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.common.PageResponse;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/prices")
public class PriceController {

    private final PriceService prices;

    public PriceController(PriceService prices) {
        this.prices = prices;
    }

    @GetMapping
    public PageResponse<PriceRecordResponse> history(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam UUID productId,
            @RequestParam UUID storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "collectedAt", "recordedAt", "id");
        return prices.findRecentHistory(productId, storeId, PageRequests.create(page, size, sort),
                AuthenticatedUser.hasPremium(user));
    }
}
