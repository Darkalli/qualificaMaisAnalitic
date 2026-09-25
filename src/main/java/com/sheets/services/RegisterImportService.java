package com.sheets.services;

import com.sheets.RegisterImportResult;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
public class RegisterImportService {
    private final RegisterCollectionService collection;
    private final RegisterPersistenceService persistence;

    public RegisterImportService(RegisterCollectionService collection, RegisterPersistenceService persistence) {
        this.collection = collection;
        this.persistence = persistence;
    }

    public RegisterImportResult importRegisters() throws IOException, GeneralSecurityException {
        // Acesso à rede e autorização Google acontecem antes da transação de banco.
        return persistence.persist(collection.collect());
    }
}
