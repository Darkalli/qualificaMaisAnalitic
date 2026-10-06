package com.example.qualificamaisanalitic.sheets;

import com.google.api.client.http.*;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.*;
import com.google.api.services.sheets.v4.Sheets;
import com.sheets.*;
import com.sheets.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class GoogleCourseCatalogWriterTests {
    final List<String> methods = new ArrayList<>(), urls = new ArrayList<>(), bodies = new ArrayList<>();
    final Queue<String> responses = new ArrayDeque<>();
    Sheets client() {
        return new Sheets.Builder(new MockHttpTransport() {
            @Override public LowLevelHttpRequest buildRequest(String method, String url) {
                methods.add(method); urls.add(url);
                return new MockLowLevelHttpRequest(url) {
                    @Override public LowLevelHttpResponse execute() throws IOException {
                        bodies.add(getContentAsString());
                        var content = responses.remove();
                        return new MockLowLevelHttpResponse().setContentType("application/json")
                            .setStatusCode(content.startsWith("ERROR:") ? Integer.parseInt(content.substring(6)) : 200)
                            .setContent(content.startsWith("ERROR:") ? "{}" : content);
                    }
                };
            }
        }, GsonFactory.getDefaultInstance(), null).setApplicationName("test").build();
    }
    SheetsProperties properties() {
        var p = new SheetsProperties(); p.setCatalogEnabled(true); p.setSpreadsheetId("test-id");
        p.setRange("'Inscrições'!A:S"); return p;
    }
    void metadata() { responses.add("{\"sheets\":[{\"properties\":{\"sheetId\":7,\"title\":\"Cursos\",\"gridProperties\":{\"rowCount\":50,\"columnCount\":8}}}]}"); }
    @Test void disabledWriterDoesNotLoadCredentialsOrContactGoogle() throws Exception {
        var p = properties(); p.setCatalogEnabled(false);
        var provider = org.mockito.Mockito.mock(GoogleSheetsClientProvider.class);
        assertThrows(IOException.class, () -> new GoogleCourseCatalogWriter(p,provider).replace(List.of()));
        org.mockito.Mockito.verifyNoInteractions(provider);
    }
    @Test void missingAuthorizationPreventsAnySheetOperation() throws Exception {
        var provider = org.mockito.Mockito.mock(GoogleSheetsClientProvider.class);
        org.mockito.Mockito.when(provider.client()).thenThrow(new IOException("Missing authorization"));
        assertThrows(IOException.class, () -> new GoogleCourseCatalogWriter(properties(),provider).replace(List.of()));
        org.mockito.Mockito.verify(provider).client(); org.mockito.Mockito.verifyNoMoreInteractions(provider);
        assertTrue(methods.isEmpty());
    }
    @Test void missingTabDoesNotWrite() {
        responses.add("{\"sheets\":[]}");
        assertThrows(IOException.class, () -> new GoogleCourseCatalogWriter(properties(), client()).replace(List.of()));
        assertEquals(List.of("GET"), methods);
    }
    @Test void sameImportTabIsRejectedBeforeAnyRequest() {
        var p = properties(); p.setRange("'Cursos'!A:S");
        assertThrows(IOException.class, () -> new GoogleCourseCatalogWriter(p, client()).replace(List.of()));
        assertTrue(methods.isEmpty());
    }
    @Test void wildcardAndDuplicateNamesAreRejectedBeforeAnyRequest() {
        for (var entries : List.of(List.of(new CourseCatalogEntry("Java*",1)),
                List.of(new CourseCatalogEntry("Java?",1)),
                List.of(new CourseCatalogEntry(" Java  básico ",1),new CourseCatalogEntry("JAVA básico",2)),
                List.of(new CourseCatalogEntry("\u00a0Java\u00a0",1),new CourseCatalogEntry("JAVA",2)))) {
            assertThrows(IOException.class, () -> new GoogleCourseCatalogWriter(properties(), client()).replace(entries));
        }
        assertTrue(methods.isEmpty());
    }
    @Test void snapshotTooLargeDoesNotWriteOrResizeSheet() {
        responses.add("{\"sheets\":[{\"properties\":{\"sheetId\":7,\"title\":\"Cursos\",\"gridProperties\":{\"rowCount\":1,\"columnCount\":2}}}]}");
        assertThrows(IOException.class, () -> new GoogleCourseCatalogWriter(properties(), client()).replace(List.of(new CourseCatalogEntry("Java",1))));
        assertEquals(List.of("GET"),methods);
    }
    @Test void googleBatchFailureDoesNotClearSeparatelyOrReadBack() {
        metadata(); responses.add("ERROR:503");
        assertThrows(IOException.class, () -> new GoogleCourseCatalogWriter(properties(),client()).replace(List.of()));
        assertEquals(List.of("GET","POST"),methods);
    }
    @Test void readbackMismatchFailsWithoutAnotherWrite() {
        metadata(); responses.add("{}"); responses.add("{\"values\":[[\"wrong\"]]}");
        assertThrows(IOException.class, () -> new GoogleCourseCatalogWriter(properties(),client()).replace(List.of()));
        assertEquals(List.of("GET","POST","GET"),methods);
    }
    @Test void fullSnapshotUsesOneAtomicBoundedValueOnlyUpdateAndVerifiesExactReadback() throws Exception {
        metadata(); responses.add("{}"); responses.add("{\"values\":[[\"Nome do curso\",\"ID do curso\"],[\"=literal\",\"9007199254740993\"]]}");
        new GoogleCourseCatalogWriter(properties(), client()).replace(List.of(new CourseCatalogEntry("=literal",9007199254740993L)));
        assertEquals(List.of("GET","POST","GET"), methods);
        var json = GsonFactory.getDefaultInstance().fromString(bodies.get(1), com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest.class);
        assertEquals(1,json.getRequests().size());
        var update = json.getRequests().getFirst().getUpdateCells();
        assertEquals("userEnteredValue",update.getFields());
        assertEquals(7,update.getRange().getSheetId()); assertEquals(0,update.getRange().getStartColumnIndex());
        assertEquals(2,update.getRange().getEndColumnIndex()); assertEquals(50,update.getRange().getEndRowIndex());
        assertEquals("9007199254740993",update.getRows().get(1).getValues().get(1).getUserEnteredValue().getStringValue());
        assertEquals("=literal",update.getRows().get(1).getValues().getFirst().getUserEnteredValue().getStringValue());
        assertNull(update.getRows().get(1).getValues().getFirst().getUserEnteredValue().getFormulaValue());
        assertTrue(urls.getLast().contains("A1:B50"));
    }
}
