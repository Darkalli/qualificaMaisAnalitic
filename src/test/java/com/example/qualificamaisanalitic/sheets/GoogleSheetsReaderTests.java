package com.example.qualificamaisanalitic.sheets;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.*;
import com.google.api.services.sheets.v4.Sheets;
import com.sheets.*;
import com.sheets.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleSheetsReaderTests {
    @Test void importsUseSharedNoninteractiveClientAndKeepExistingReadContract() throws Exception {
        var p = new SheetsProperties(); p.setSpreadsheetId("test-id"); p.setRange("'Import'!A:S");
        var provider = mock(GoogleSheetsClientProvider.class);
        var client = new Sheets.Builder(new MockHttpTransport.Builder().setLowLevelHttpResponse(
            new MockLowLevelHttpResponse().setContentType("application/json").setContent("{\"values\":[[\"Nome\",\"ID do curso\"],[\"Test\",\"1\"]]}")).build(),
            GsonFactory.getDefaultInstance(),null).setApplicationName("test").build();
        when(provider.client()).thenReturn(client);
        assertEquals(List.of(List.of("Nome","ID do curso"),List.of("Test","1")),new GoogleSheetsReader(p,provider).read());
        verify(provider).client(); verify(provider,never()).authorizeInteractively();
    }
    @Test void missingStoredAuthorizationFailsWithoutCallingInteractiveAuthorization() throws Exception {
        var p = new SheetsProperties(); p.setSpreadsheetId("test-id"); p.setRange("'Import'!A:S");
        var provider = mock(GoogleSheetsClientProvider.class);
        when(provider.client()).thenThrow(new IOException("Stored authorization missing"));
        assertThrows(IOException.class, () -> new GoogleSheetsReader(p,provider).read());
        verify(provider,never()).authorizeInteractively();
    }
}
