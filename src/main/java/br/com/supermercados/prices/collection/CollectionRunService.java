package br.com.supermercados.prices.collection;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.common.PageResponse;
import lombok.RequiredArgsConstructor;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class CollectionRunService {

    private final CollectionRunRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CollectionRunResponse start(CollectorMetadata metadata, boolean fullImport) {
        return CollectionRunResponse.from(repository.save(new CollectionRun(metadata, clock.instant(), fullImport)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CollectionRunResponse finish(UUID runId, CollectionResult result) {
        CollectionRun run = requireRun(runId);
        run.finish(result, clock.instant());
        return CollectionRunResponse.from(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CollectionRunResponse fail(UUID runId, String message) {
        CollectionRun run = requireRun(runId);
        run.fail(message, clock.instant());
        return CollectionRunResponse.from(run);
    }

    @Transactional(readOnly = true)
    public PageResponse<CollectionRunResponse> findAll(Pageable pageable) {
        return PageResponse.from(repository.findAll(pageable).map(CollectionRunResponse::from));
    }

    @Transactional(readOnly = true)
    public CollectionRunResponse find(UUID runId) {
        return CollectionRunResponse.from(requireRun(runId));
    }

    private CollectionRun requireRun(UUID runId) {
        return repository.findById(runId)
                .orElseThrow(() -> new ApiException(NOT_FOUND, "Execução de coleta não encontrada"));
    }
}
