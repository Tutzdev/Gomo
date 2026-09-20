package br.com.supermercados.prices.product;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.common.PageResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductOfferService productOffers;
    private final ProductDiscoveryService discovery;

    @GetMapping("/discovery")
    public PageResponse<ProductDiscoveryResult> discover(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String gtin,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID cityId,
            @RequestParam(required = false) List<UUID> storeIds,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) BigDecimal quantity,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(discovery.search(new ProductSearch(query, brand, gtin, category,
                storeId, unit, quantity, sort), cityId, storeIds, PageRequests.create(page, size, Sort.unsorted())));
    }

    @GetMapping("/offers")
    public List<ProductOffers> offers(@RequestParam List<UUID> ids, @RequestParam UUID cityId,
            @RequestParam(required = false) List<UUID> storeIds) {
        return productOffers.findOffers(ids, cityId, storeIds);
    }

    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String gtin,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) BigDecimal quantity,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(productService.search(new ProductSearch(query, brand, gtin, category,
                storeId, unit, quantity, sort),
                PageRequests.create(page, size, Sort.by("name", "id"))));
    }

    @GetMapping("/filters")
    public ProductSearchFacets filters(@RequestParam(required = false) String query) {
        return productService.searchFacets(new ProductSearch(query, null, null, null));
    }

    @GetMapping("/{id}")
    public ProductResponse findProduct(@PathVariable UUID id) {
        return productService.findProduct(id);
    }
}
