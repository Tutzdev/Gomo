package br.com.supermercados.prices.collection.flyer;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;

import br.com.supermercados.prices.collection.CollectedCatalog;
import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.collection.CollectedStore;
import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.collection.SupermarketCollector;
import br.com.supermercados.prices.price.StockAvailability;
import tools.jackson.databind.ObjectMapper;

/** Imports a reviewed public flyer, not a live stock feed. Replaying never refreshes its observation date. */
public final class ReviewedAtacadaoFlyerCollector implements SupermarketCollector {

    private static final ZoneId LOCAL_ZONE = ZoneId.of("America/Sao_Paulo");
    private final int storeId;
    private final String code;
    private final CollectedStore store;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ReviewedAtacadaoFlyerCollector(int storeId, String code, CollectedStore store, ObjectMapper mapper, Clock clock) {
        this.storeId = storeId;
        this.code = code;
        this.store = store;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public CollectorMetadata metadata() {
        return new CollectorMetadata(code, "Atacadão", store.name(), "atacadao_reviewed_flyers",
                "Encartes oficiais Atacadão — revisão manual", "https://www.atacadao.com.br",
                Instant.parse("2026-09-19T00:20:00Z"), "Atacadão", "atacadao:chain",
                "atacadao:store:" + storeId, UUID.fromString("5c4cb935-52e1-4bf8-8d17-902dc0837c66"));
    }

    @Override
    public CollectedCatalog collect() {
        ReviewedFlyers reviewed;
        try (var input = new ClassPathResource("collectors/atacadao-reviewed-2026-09.json").getInputStream()) {
            reviewed = mapper.readValue(input, ReviewedFlyers.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Encartes revisados não encontrados", exception);
        }
        if (!"MANUALLY_REVIEWED_PUBLIC_FLYERS".equals(reviewed.method()) || !reviewed.storeIds().contains(storeId)) {
            throw new IllegalStateException("Encartes sem revisão ou sem abrangência para a unidade");
        }
        Instant now = clock.instant();
        if (reviewed.reviewedAt().isAfter(now)) {
            throw new IllegalStateException("Data de revisão dos encartes está no futuro");
        }
        List<CollectedProduct> products = new ArrayList<>();
        for (Flyer document : reviewed.documents()) {
            Instant start = document.validFrom().atStartOfDay(LOCAL_ZONE).toInstant();
            Instant end = document.validThrough().plusDays(1).atStartOfDay(LOCAL_ZONE).toInstant();
            if (now.isBefore(start) || !now.isBefore(end)) continue;
            if (!document.sha256().matches("[a-f0-9]{64}") || !document.url().equals(
                    "https://apigw.cloud.carrefour.com.br/api-middleware-flyer-services/api/v2/Flyer/?id=" + document.id())) {
                throw new IllegalStateException("Origem do encarte revisado inválida");
            }
            for (FlyerProduct product : document.products()) {
                products.add(new CollectedProduct("atacadao:flyer-product:" + product.reference(),
                        product.name(), null, product.brand(),
                        "Oferta de encarte para loja física; estoque sujeito à confirmação. Revisão da fonte: "
                                + reviewed.reviewedAt().atZone(LOCAL_ZONE).toLocalDate() + ".",
                        product.category(), product.price(), product.appPrice(), product.appPrice() == null ? null
                                : "Ativar a oferta no aplicativo Meu Atacadão; consulte o limite por cliente. Não aplicado ao total.",
                        end, product.appPrice() == null ? null : end, StockAvailability.UNKNOWN, null, document.url()));
            }
        }
        if (products.isEmpty()) {
            throw new IllegalStateException("Encartes revisados vencidos; é necessária nova revisão da fonte oficial");
        }
        return new CollectedCatalog(store, List.copyOf(products), products.size(), reviewed.reviewedAt(), List.of());
    }

    public record ReviewedFlyers(Instant reviewedAt, String method, List<Integer> storeIds,
            String storeDirectoryUrl, List<Flyer> documents) {
    }

    public record Flyer(String id, String url, String sha256, LocalDate validFrom,
            LocalDate validThrough, List<FlyerProduct> products) {
    }

    public record FlyerProduct(String reference, String name, String brand, String category,
            BigDecimal price, BigDecimal appPrice) {
    }
}
