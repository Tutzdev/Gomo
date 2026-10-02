package br.com.supermercados.prices.collection;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Bounded, sequential access to public storefronts; a new instance belongs to one collection. */
public final class PublicCatalogHttp {

    private static final Set<Integer> RETRYABLE = Set.of(502, 503, 504);
    private static final int MAX_BYTES = 8_000_000;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Duration delay;
    private final Duration timeout;
    private boolean requested;

    public PublicCatalogHttp(Duration delay) {
        this(delay, Duration.ofSeconds(25));
    }

    PublicCatalogHttp(Duration delay, Duration timeout) {
        if (delay.isNegative()) throw new IllegalArgumentException("Intervalo de coleta inválido");
        this.delay = delay;
        this.timeout = timeout;
    }

    public String get(URI uri) {
        return request(uri, Map.of(), null);
    }

    public String request(URI uri, Map<String, String> headers, String body) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                if (requested) Thread.sleep(delay.toMillis() * attempt);
                requested = true;
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout)
                        .header("User-Agent", "Gomo/1.0 public-price-collector")
                        .header("Accept", "application/json, text/html");
                headers.forEach(builder::header);
                if (body == null) builder.GET();
                else builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));

                var response = fetch(builder.build());
                if (RETRYABLE.contains(response.statusCode()) && attempt < 2) continue;
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Fonte respondeu HTTP " + response.statusCode()
                            + " em " + uri.getHost() + uri.getPath());
                }
                return new String(response.body(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                if (attempt == 2) throw new IllegalStateException("Falha de comunicação com " + uri.getHost(), exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Coleta interrompida", exception);
            }
        }
        throw new IllegalStateException("Coleta não concluída");
    }

    private HttpResponse<byte[]> fetch(HttpRequest request) throws IOException, InterruptedException {
        var pending = client.sendAsync(request, ignored -> new LimitedBody());
        try {
            return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw new HttpTimeoutException("Prazo excedido ao receber o catálogo");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException communication) throw communication;
            throw new IllegalStateException("Resposta pública inválida", exception.getCause());
        } catch (InterruptedException exception) {
            pending.cancel(true);
            throw exception;
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long received;

        @Override
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) received += buffer.remaining();
            if (received > MAX_BYTES) {
                subscription.cancel();
                delegate.onError(new IllegalStateException("Catálogo excede o limite de resposta"));
                return;
            }
            delegate.onNext(buffers);
        }

        @Override
        public void onError(Throwable failure) { delegate.onError(failure); }

        @Override
        public void onComplete() { delegate.onComplete(); }
    }
}
