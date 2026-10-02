package br.com.supermercados.prices.subscription;

import br.com.supermercados.prices.common.ApiException;
import org.springframework.http.HttpStatus;

/** A free account tried something only Premium allows. */
public class PlanLimitException extends ApiException {

    private final PlanLimit limit;

    public PlanLimitException(PlanLimit limit, String message) {
        super(HttpStatus.FORBIDDEN, message);
        this.limit = limit;
    }

    public PlanLimit getLimit() {
        return limit;
    }
}
