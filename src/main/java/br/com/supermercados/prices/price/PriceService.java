package br.com.supermercados.prices.price;

import br.com.supermercados.prices.alert.PriceAlertEvaluator;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.datasource.DataSourceService;
import br.com.supermercados.prices.product.ProductService;
import br.com.supermercados.prices.store.StoreRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceService {

    private final PriceRecordRepository prices;
    private final ProductService products;
    private final StoreRepository stores;
    private final DataSourceService sources;
    private final Validator validator;
    private final PriceObservationRules observationRules;
    private final PriceAlertEvaluator alertEvaluator;
    private final Clock clock;

    public PriceService(PriceRecordRepository prices, ProductService products, StoreRepository stores,
            DataSourceService sources, Validator validator,
            PriceObservationRules observationRules, PriceAlertEvaluator alertEvaluator, Clock clock) {
        this.prices = prices;
        this.products = products;
        this.stores = stores;
        this.sources = sources;
        this.validator = validator;
        this.observationRules = observationRules;
        this.alertEvaluator = alertEvaluator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PriceRecordResponse> findHistory(UUID productId, UUID storeId, Pageable pageable) {
        requireProductAndStore(productId, storeId);
        return PageResponse.from(prices.findByProductIdAndStoreId(productId, storeId, pageable)
                .map(PriceRecordResponse::from));
    }

    /** Internal ingestion boundary. Reusing a source reference with changed content is a conflict. */
    @Transactional
    public PriceRecordResponse appendObservation(PriceObservation observation) {
        return append(observation, null);
    }

    @Transactional
    public PriceRecordResponse appendUserContribution(PriceObservation observation, UUID contributionId) {
        if (contributionId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A contribuição é obrigatória.");
        }
        return append(observation, contributionId);
    }

    private PriceRecordResponse append(PriceObservation observation, UUID contributionId) {
        Instant now = clock.instant();
        validateObservation(observation, now);
        requireProductAndStore(observation.productId(), observation.storeId());
        sources.requireEnabledSource(observation.sourceId());

        PriceRecord candidate = contributionId == null
                ? PriceRecord.from(observation, now)
                : PriceRecord.fromContribution(observation, contributionId, now);
        prices.insertIfAbsent(candidate);
        PriceRecord persisted = prices.findBySourceIdAndSourceReference(
                candidate.getSourceId(), candidate.getSourceReference()).orElseThrow();
        if (!persisted.hasSameObservation(candidate)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "A referência da fonte já identifica outra observação de preço.");
        }
        if (persisted.getOriginUrl() == null && candidate.getOriginUrl() != null
                && prices.fillMissingOrigin(persisted.getId(), candidate.getOriginUrl()) > 0) {
            persisted.enrichOrigin(candidate.getOriginUrl());
        }
        if (persisted.getSourceProductName() == null && candidate.getSourceProductName() != null
                && prices.fillMissingSourceDescription(persisted.getId(), candidate.getSourceProductName()) > 0) {
            persisted.enrichSourceDescription(candidate.getSourceProductName());
        }
        alertEvaluator.evaluate(persisted.getId());
        return PriceRecordResponse.from(persisted);
    }

    private void requireProductAndStore(UUID productId, UUID storeId) {
        products.requireProduct(productId);
        if (!stores.existsById(storeId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Loja não encontrada.");
        }
    }

    private void validateObservation(PriceObservation observation, Instant now) {
        if (observation == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A observação de preço é obrigatória.");
        }
        var violations = validator.validate(observation);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        observationRules.validate(observation.regularPrice(), observation.promotionalPrice(),
                observation.collectedAt(), observation.validUntil(), observation.promotionValidUntil(),
                observation.promotionCondition(), now);
    }
}
