package br.com.supermercados.prices.contribution;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.datasource.DataSource;
import br.com.supermercados.prices.datasource.DataSourceService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class ContributionSource {

    private final DataSourceService sources;
    private final String configuredSourceId;

    ContributionSource(DataSourceService sources, @Value("${app.contributions.source-id:}") String configuredSourceId) {
        this.sources = sources;
        this.configuredSourceId = configuredSourceId;
    }

    DataSource requireEnabledSource() {
        if (configuredSourceId.isBlank()) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "A fonte interna de contribuições ainda não foi configurada.");
        }

        try {
            return sources.requireEnabledSource(UUID.fromString(configuredSourceId));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.contributions.source-id must be a UUID", exception);
        }
    }
}
