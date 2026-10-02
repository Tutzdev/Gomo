package br.com.supermercados.prices.collection;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import br.com.supermercados.prices.common.PageResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/** Unmatched offers remain review evidence; they never become public prices. */
@Repository
@RequiredArgsConstructor
public class CollectionReviewRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PageResponse<CollectionReviewResponse> findAll(Pageable pageable) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM collection_review_items", Long.class);
        var items = jdbc.query("""
                SELECT * FROM collection_review_items
                ORDER BY collected_at DESC, source_id, store_id, source_reference LIMIT ? OFFSET ?
                """, (row, index) -> new CollectionReviewResponse(row.getObject("source_id", UUID.class),
                row.getObject("store_id", UUID.class), row.getString("source_reference"),
                row.getTimestamp("collected_at").toInstant(), row.getString("reason"),
                mapper.readValue(row.getString("offer"), CollectedProduct.class)), pageable.getPageSize(), pageable.getOffset());
        return PageResponse.from(new PageImpl<>(items, pageable, count == null ? 0 : count));
    }

    public void record(UUID sourceId, UUID storeId, CollectedProduct product, Instant collectedAt, String reason) {
        jdbc.update("""
                INSERT INTO collection_review_items (source_id, store_id, source_reference, collected_at, reason, offer)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (source_id, store_id, source_reference) DO UPDATE
                SET collected_at = excluded.collected_at, reason = excluded.reason, offer = excluded.offer
                WHERE collection_review_items.collected_at <= excluded.collected_at
                """, sourceId, storeId, product.sourceReference(), Timestamp.from(collectedAt),
                reason.substring(0, Math.min(reason.length(), 2000)), mapper.writeValueAsString(product));
    }

    public void resolve(UUID sourceId, UUID storeId, String reference) {
        jdbc.update("DELETE FROM collection_review_items WHERE source_id = ? AND store_id = ? AND source_reference = ?",
                sourceId, storeId, reference);
    }
}
