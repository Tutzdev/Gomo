package br.com.supermercados.prices.datasource;

import java.net.URI;
import java.time.Clock;
import java.util.UUID;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataSourceService {

    private final DataSourceRepository repository;
    private final ObservationValidator validator;
    private final Clock clock;

    public DataSource requireEnabledSource(UUID sourceId) {
        DataSource source = repository.findById(sourceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Fonte de dados não encontrada"));

        if (!source.isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "Fonte de dados desabilitada");
        }

        return source;
    }

    public void verifyObservation(SourceObservation observation) {
        validator.validate(observation);
        requireEnabledSource(observation.sourceId());

        if (observation.collectedAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Coleta não pode estar no futuro");
        }
    }

    public DataSource requireSource(UUID sourceId) {
        return repository.findById(sourceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Fonte de dados não encontrada"));
    }

    public Optional<DataSource> findByCode(String code) {
        return repository.findByCode(code);
    }

    @Transactional
    public DataSource registerVerifiedSource(SourceRegistration registration) {
        validator.validate(registration);
        validateSourceUrl(registration.baseUrl());

        if (registration.verifiedAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Verificação não pode estar no futuro");
        }

        if (repository.existsByCode(registration.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "Código de fonte já cadastrado");
        }

        return repository.save(new DataSource(registration, clock.instant()));
    }

    @Transactional
    public DataSourceResponse changeEnabled(UUID sourceId, boolean enabled) {
        DataSource source = requireSource(sourceId);
        source.changeEnabled(enabled);

        return DataSourceResponse.from(source);
    }

    private void validateSourceUrl(String baseUrl) {
        URI uri;

        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "URL da fonte inválida");
        }

        boolean supportedScheme = "https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme());

        if (!supportedScheme || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Fonte deve informar URL HTTP(S) sem credenciais");
        }
    }
}
