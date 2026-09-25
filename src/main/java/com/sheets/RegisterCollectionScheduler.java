package com.sheets;

import com.sheets.services.RegisterImportService;
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
    private final RegisterImportService service;

    public RegisterCollectionScheduler(RegisterImportService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.sheets.check-interval:5m}",
            initialDelayString = "${app.sheets.check-initial-delay:10s}")
    public void check() {
        try {
            var result = service.importRegisters();
            log.info("Importação da planilha: {} novos, {} sem alteração, {} divergências, {} linhas com erro, {} linhas vazias.",
                    result.inserted(), result.unchanged(), result.conflicts().size(), result.errors().size(), result.ignoredRows());
            result.conflicts().forEach(conflict -> log.warn("Cadastro {} preservado; campos divergentes: {}",
                    conflict.registerId(), conflict.fields()));
            result.errors().forEach(error -> log.warn("Linha {}: {}", error.row(), error.message()));
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            // Exceções SQL podem conter CPF e outros valores pessoais; não registrar a mensagem/stack trace.
            log.error("Falha na importação ({}). Uma nova tentativa será feita no próximo intervalo.",
                    exception.getClass().getSimpleName());
        }
    }
}
