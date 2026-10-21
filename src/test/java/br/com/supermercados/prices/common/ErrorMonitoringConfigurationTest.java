package br.com.supermercados.prices.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.SentryOptions;
import io.sentry.spring.boot4.SentryAutoConfiguration;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import io.sentry.protocol.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ErrorMonitoringConfigurationTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SentryAutoConfiguration.class))
            .withUserConfiguration(ErrorMonitoringConfiguration.class);

    @Test
    void sentryDoesNotStartWithoutDsn() {
        contexts.run(context -> assertThat(context).doesNotHaveBean(SentryOptions.class));
    }

    @Test
    void sentryWithDsnSendsThroughTheScrubberWithoutPersonalData() {
        contexts.withPropertyValues("sentry.dsn=https://public@o0.ingest.sentry.invalid/1", "sentry.send-default-pii=false")
                .run(context -> {
                    SentryOptions options = context.getBean(SentryOptions.class);
                    assertThat(options.getBeforeSend()).isSameAs(context.getBean(SentryOptions.BeforeSendCallback.class));
                    assertThat(options.isSendDefaultPii()).isFalse();
                    assertThat(options.getTracesSampleRate()).isNull();
                });
        Sentry.close();
    }

    @Test
    void reportKeepsTheErrorTypeAndPathButNotMessagesOrPersonalData() {
        SentryEvent event = new SentryEvent(new IllegalStateException("insert ... values ('pessoa@example.com')"));
        Request request = new Request();
        request.setUrl("https://gomo.example.test/api/v1/users/me");
        request.setQueryString("email=pessoa@example.com");
        request.setData(Map.of("password", "segredo"));
        request.setCookies("__Host-gomo_session=abc");
        request.setHeaders(Map.of("Authorization", "Bearer abc"));
        event.setRequest(request);
        User user = new User();
        user.setEmail("pessoa@example.com");
        event.setUser(user);
        event.setBreadcrumbs(List.of(new Breadcrumb("GET /api/v1/users/me?email=pessoa@example.com")));
        event.setExceptions(List.of(new io.sentry.protocol.SentryException()));
        event.getExceptions().getFirst().setType("IllegalStateException");
        event.getExceptions().getFirst().setValue("insert ... values ('pessoa@example.com')");

        SentryEvent scrubbed = ErrorMonitoringConfiguration.scrub(event);

        assertThat(scrubbed.getExceptions()).singleElement().satisfies(exception -> {
            assertThat(exception.getType()).isEqualTo("IllegalStateException");
            assertThat(exception.getValue()).isNull();
        });
        assertThat(scrubbed.getRequest().getUrl()).isEqualTo("https://gomo.example.test/api/v1/users/me");
        assertThat(scrubbed.getRequest().getQueryString()).isNull();
        assertThat(scrubbed.getRequest().getData()).isNull();
        assertThat(scrubbed.getRequest().getCookies()).isNull();
        assertThat(scrubbed.getRequest().getHeaders()).isNull();
        assertThat(scrubbed.getUser()).isNull();
        assertThat(scrubbed.getBreadcrumbs()).isNull();
    }
}
