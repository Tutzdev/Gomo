package br.com.supermercados.prices.deal;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.PageRequests;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public: anyone can see the city's deals; only the "my lists" filter needs a Premium account. */
@RestController
public class DealController {

    private final DealService deals;

    public DealController(DealService deals) {
        this.deals = deals;
    }

    @GetMapping("/api/v1/deals")
    public DealsResponse find(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam UUID cityId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean onlyMyLists,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return deals.find(user, cityId, storeId, category, onlyMyLists, PageRequests.create(page, size, Sort.unsorted()));
    }
}
