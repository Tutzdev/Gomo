package br.com.supermercados.prices.suggestion;

import br.com.supermercados.prices.contribution.ContributionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_suggestions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProductSuggestion {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID contributorId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 120)
    private String brand;

    @Column(length = 80)
    private String packageSize;

    @Column(length = 14)
    private String barcode;

    private UUID storeId;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContributionStatus status;

    private UUID moderatorId;

    private Instant decidedAt;

    @Column(length = 500)
    private String rejectionReason;

    @Version
    private long version;

    ProductSuggestion(UUID contributorId, SubmitProductSuggestionRequest request, Instant now) {
        id = UUID.randomUUID();
        this.contributorId = contributorId;
        name = request.name().strip();
        brand = clean(request.brand());
        packageSize = clean(request.packageSize());
        barcode = clean(request.barcode());
        storeId = request.storeId();
        price = request.price();
        note = clean(request.note());
        submittedAt = now;
        status = ContributionStatus.PENDING;
    }

    boolean isPending() {
        return status == ContributionStatus.PENDING;
    }

    void approve(UUID moderatorId, Instant now) {
        status = ContributionStatus.APPROVED;
        this.moderatorId = moderatorId;
        decidedAt = now;
    }

    void reject(UUID moderatorId, String reason, Instant now) {
        status = ContributionStatus.REJECTED;
        this.moderatorId = moderatorId;
        rejectionReason = reason.strip();
        decidedAt = now;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
