package br.com.supermercados.prices.product;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository repository;
    private final ProductSearchRepository searchRepository;
    private final ProductComparisonEvidence evidence;

    public Page<ProductResponse> search(ProductSearch search, Pageable pageable) {
        return searchRepository.search(search, pageable);
    }

    public ProductSearchFacets searchFacets(ProductSearch search) {
        return searchRepository.facets(search);
    }

    public ProductResponse findProduct(UUID productId) {
        Product product = requireProduct(productId);
        return ProductResponse.from(product, comparisonName(product));
    }

    public String comparisonName(Product product) {
        return evidence.displayName(product);
    }

    public Page<ProductResponse> searchInStore(UUID storeId, ProductSearch search, Pageable pageable) {
        return searchRepository.search(search.inStore(storeId), pageable);
    }

    public Product requireProduct(UUID productId) {
        return repository.findById(productId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Produto não encontrado"));
    }
}
