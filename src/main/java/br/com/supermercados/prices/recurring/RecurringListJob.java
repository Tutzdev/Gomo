package br.com.supermercados.prices.recurring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Once a day, after the morning collection, today's total of every weekly and monthly list. */
@Component
class RecurringListJob {

    private static final Logger log = LoggerFactory.getLogger(RecurringListJob.class);

    private final RecurringListService recurringLists;

    RecurringListJob(RecurringListService recurringLists) {
        this.recurringLists = recurringLists;
    }

    @Scheduled(cron = "${app.recurring-lists.cron:0 0 10 * * *}", zone = "${app.collection.zone:America/Sao_Paulo}")
    void recalculate() {
        try {
            log.info("Recurring lists recalculated: {}", recurringLists.snapshotAll());
        } catch (RuntimeException exception) {
            log.error("Recurring lists were not recalculated", exception);
        }
    }
}
