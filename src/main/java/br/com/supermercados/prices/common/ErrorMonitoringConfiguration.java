package br.com.supermercados.prices.common;

import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ErrorMonitoringConfiguration {

    @Bean
    SentryOptions.BeforeSendCallback errorReportScrubber() {
        return (event, hint) -> scrub(event);
    }

    static SentryEvent scrub(SentryEvent event) {
        if (event.getExceptions() != null) {
            event.getExceptions().forEach(exception -> exception.setValue(null));
        }

        if (event.getRequest() != null) {
            event.getRequest().setQueryString(null);
            event.getRequest().setData(null);
            event.getRequest().setCookies(null);
            event.getRequest().setHeaders(null);
        }

        event.setUser(null);
        event.setBreadcrumbs(null);

        return event;
    }
}
