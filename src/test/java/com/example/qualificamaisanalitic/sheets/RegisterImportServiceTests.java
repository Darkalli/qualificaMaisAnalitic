package com.example.qualificamaisanalitic.sheets;

import com.sheets.*;
import com.sheets.services.RegisterCollectionService;
import com.sheets.services.RegisterImportService;
import com.sheets.services.RegisterPersistenceService;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegisterImportServiceTests {
    @Test
    void persistsCollectedRowsAndReturnsThePersistenceReport() throws Exception {
        var collection = mock(RegisterCollectionService.class);
        var persistence = mock(RegisterPersistenceService.class);
        var rows = new SheetImportResult(List.of(), List.of(), 0);
        var report = new RegisterImportResult(0, 0, List.of(), List.of(), 0);
        when(collection.collect()).thenReturn(rows);
        when(persistence.persist(rows)).thenReturn(report);
        assertSame(report, new RegisterImportService(collection, persistence).importRegisters());
    }

    @Test
    void readFailureDoesNotReachPersistence() throws Exception {
        var collection = mock(RegisterCollectionService.class);
        var persistence = mock(RegisterPersistenceService.class);
        when(collection.collect()).thenThrow(new IOException("Falha simulada"));
        assertThrows(IOException.class, () -> new RegisterImportService(collection, persistence).importRegisters());
        verifyNoInteractions(persistence);
    }
}
