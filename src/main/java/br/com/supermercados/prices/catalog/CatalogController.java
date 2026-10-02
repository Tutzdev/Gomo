package br.com.supermercados.prices.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.common.PageResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalog;
    private final CatalogBuilder builder;

    @GetMapping("/api/v1/catalog/items")
    public PageResponse<CatalogItemResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID cityId,
            @RequestParam(required = false) List<UUID> storeIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(catalog.search(query, cityId, storeIds, PageRequests.create(page, size, Sort.unsorted())));
    }

    @GetMapping("/api/v1/catalog/items/{id}")
    public CatalogItemDetailResponse find(@PathVariable UUID id, @RequestParam(required = false) UUID cityId,
            @RequestParam(required = false) List<UUID> storeIds) {
        return catalog.find(id, cityId, storeIds);
    }

    @PostMapping("/api/v1/admin/catalog/rebuild")
    public CatalogBuilder.Summary rebuild() {
        return builder.rebuild();
    }
}
