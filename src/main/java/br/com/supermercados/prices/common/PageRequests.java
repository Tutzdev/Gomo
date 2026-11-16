package br.com.supermercados.prices.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

public final class PageRequests {

    private PageRequests() {}

    public static PageRequest create(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Paginação inválida: page >= 0 e size entre 1 e 100.");
        }

        return PageRequest.of(page, size, sort);
    }
}
