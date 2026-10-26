package br.com.supermercados.prices.recurring;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecurringListController {

    private final RecurringListService recurringLists;

    public RecurringListController(RecurringListService recurringLists) {
        this.recurringLists = recurringLists;
    }

    @GetMapping("/api/v1/recurring-lists")
    public List<RecurringListResponse> mine(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam UUID cityId) {
        return recurringLists.mine(user.id(), cityId);
    }
}
