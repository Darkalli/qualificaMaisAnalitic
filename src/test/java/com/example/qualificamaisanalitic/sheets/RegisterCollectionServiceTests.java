package com.example.qualificamaisanalitic.sheets;

import com.sheets.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegisterCollectionServiceTests {
    @Test
    void passesSheetValuesAndHeaderOffsetToMapper() throws Exception {
        var reader = mock(GoogleSheetsReader.class);
        var mapper = mock(RegisterSheetMapper.class);
        var properties = new SheetsProperties();
        properties.setHeaderRow(5);
        List<List<Object>> values = List.of(List.of("Nome completo"));
        var expected = new SheetImportResult(List.of(), List.of(), 0);
        when(reader.read()).thenReturn(values);
        when(mapper.map(values, 5)).thenReturn(expected);
        assertSame(expected, new RegisterCollectionService(reader, mapper, properties).collect());
        verify(mapper).map(values, 5);
    }

    @Test
    void propagatesReadFailuresWithoutReturningAnEmptySuccess() throws Exception {
        var reader = mock(GoogleSheetsReader.class);
        var mapper = mock(RegisterSheetMapper.class);
        when(reader.read()).thenThrow(new IOException("Falha na leitura"));
        var service = new RegisterCollectionService(reader, mapper, new SheetsProperties());
        assertThrows(IOException.class, service::collect);
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsMissingSpreadsheetIdBeforeStartingAuthorization() {
        var properties = new SheetsProperties();
        properties.setSpreadsheetId("");
        var reader = new GoogleSheetsReader(properties);
        assertThrows(IllegalArgumentException.class, reader::read);
    }
}
