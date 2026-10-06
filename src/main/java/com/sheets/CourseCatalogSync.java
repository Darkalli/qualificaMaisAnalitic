package com.sheets;

import com.repositories.CourseRepository;
import com.sheets.config.SheetsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.IOException;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

/** Serialized snapshots, not event payloads: a queued old event can never restore an old snapshot. */
@Component
public class CourseCatalogSync {
    private static final Logger LOG = LoggerFactory.getLogger(CourseCatalogSync.class);
    private final CourseRepository repository;
    private final GoogleCourseCatalogWriter writer;
    private final SheetsProperties properties;
    private final AtomicBoolean requested = new AtomicBoolean();

    public CourseCatalogSync(CourseRepository repository, GoogleCourseCatalogWriter writer, SheetsProperties properties) {
        this.repository = repository; this.writer = writer; this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void courseChanged(CourseCatalogChanged event) {
        // After-commit callback only invalidates; no repository or network operation on the CRUD thread.
        if (properties.isCatalogEnabled()) requested.set(true);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        if (properties.isCatalogEnabled()) requested.set(true);
    }

    @Scheduled(fixedDelayString = "${app.sheets.catalog-dispatch-interval-ms:1000}",
               initialDelayString = "${app.sheets.catalog-initial-delay-ms:10000}")
    public synchronized void flushRequested() {
        if (requested.getAndSet(false)) reconcile();
    }

    @Scheduled(fixedDelayString = "${app.sheets.catalog-interval-ms:300000}",
               initialDelayString = "${app.sheets.catalog-initial-delay-ms:10000}")
    public synchronized void reconcile() {
        if (!properties.isCatalogEnabled()) return;
        // Defensive guard also protects direct callers; Spring's scheduler runs outside a DB transaction.
        if (TransactionSynchronizationManager.isActualTransactionActive()) { requested.set(true); return; }
        requested.set(false);
        try {
            var entries = repository.findAll().stream().map(c -> new CourseCatalogEntry(c.getName(), c.getId()))
                .sorted(Comparator.comparingLong(CourseCatalogEntry::id)).toList();
            GoogleCourseCatalogWriter.validateEntries(entries);
            writer.replace(entries);
        } catch (IOException | RuntimeException failure) {
            // No exception message, course names, credential, URL or stack trace can leak through logging.
            LOG.warn("Course catalogue reconciliation failed; periodic reconciliation will retry");
        }
    }
}
