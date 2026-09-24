package com.sheets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Component
@ConditionalOnProperty(prefix = "app.sheets", name = "check-enabled", havingValue = "true", matchIfMissing = true)
public class RegisterCollectionScheduler {
    private static final Logger log = LoggerFactory.getLogger(RegisterCollectionScheduler.class);
    private final RegisterCollectionService service;

    public RegisterCollectionScheduler(RegisterCollectionService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.sheets.check-interval:5m}",
            initialDelayString = "${app.sheets.check-initial-delay:10s}")
    public void check() {
        try {
            var result = service.collect();
            log.info("Checagem da planilha: {} cadastros válidos, {} linhas com erro, {} linhas vazias.",
                    result.registers().size(), result.errors().size(), result.ignoredRows());
            result.errors().forEach(error -> log.warn("Linha {}: {}", error.row(), error.message()));
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            log.error("Falha na checagem da planilha. Uma nova tentativa será feita no próximo intervalo.", exception);
        }
    }
}
