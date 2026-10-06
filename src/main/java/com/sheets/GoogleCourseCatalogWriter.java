package com.sheets;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import com.sheets.config.SheetsProperties;
import java.io.IOException;
import java.util.*;

@org.springframework.stereotype.Component
public class GoogleCourseCatalogWriter {
    private final SheetsProperties properties;
    private final Sheets client;
    private final GoogleSheetsClientProvider provider;
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleCourseCatalogWriter(SheetsProperties properties, GoogleSheetsClientProvider provider) {
        this.properties = properties; this.provider = provider; this.client = null;
    }
    public GoogleCourseCatalogWriter(SheetsProperties properties, Sheets client) {
        this.properties = properties; this.client = client; this.provider = null;
    }
    public synchronized void replace(List<CourseCatalogEntry> entries) throws IOException {
        validate(entries);
        Sheets client;
        try { client = this.client != null ? this.client : provider.client(); }
        catch (java.security.GeneralSecurityException e) { throw new IOException("Sheets client unavailable"); }
        var metadata = client.spreadsheets().get(properties.getSpreadsheetId())
            .setFields("sheets(properties(sheetId,title,gridProperties(rowCount,columnCount)))").execute();
        var sheet = metadata.getSheets().stream().map(Sheet::getProperties)
            .filter(s -> "Cursos".equals(s.getTitle())).findFirst().orElseThrow(() -> new IOException("Catalogue tab unavailable"));
        int height = sheet.getGridProperties().getRowCount();
        if (height < entries.size() + 1 || sheet.getGridProperties().getColumnCount() < 2)
            throw new IOException("Catalogue grid capacity insufficient");
        var expected = new ArrayList<List<Object>>();
        expected.add(List.of("Nome do curso", "ID do curso"));
        for (var entry : entries) expected.add(List.of(entry.name(), Long.toString(entry.id())));
        var rows = expected.stream().map(row -> new RowData().setValues(row.stream()
            .map(value -> new CellData().setUserEnteredValue(new ExtendedValue().setStringValue(value.toString()))).toList())).toList();
        var update = new UpdateCellsRequest().setRange(new GridRange().setSheetId(sheet.getSheetId())
            .setStartRowIndex(0).setEndRowIndex(height).setStartColumnIndex(0).setEndColumnIndex(2))
            .setFields("userEnteredValue").setRows(rows);
        client.spreadsheets().batchUpdate(properties.getSpreadsheetId(), new BatchUpdateSpreadsheetRequest()
            .setRequests(List.of(new Request().setUpdateCells(update)))).execute();
        var actual = client.spreadsheets().values().get(properties.getSpreadsheetId(), "'Cursos'!A1:B" + height)
            .setValueRenderOption("UNFORMATTED_VALUE").execute().getValues();
        if (!expected.equals(actual)) throw new IOException("Catalogue verification failed");
    }
    private void validate(List<CourseCatalogEntry> entries) throws IOException {
        if (!properties.isCatalogEnabled() || !"Cursos".equals(properties.getCatalogSheetName())
            || properties.getSpreadsheetId() == null || properties.getSpreadsheetId().isBlank())
            throw new IOException("Catalogue configuration invalid or disabled");
        String range = properties.getRange();
        if (range == null || !range.contains("!")) throw new IOException("Import tab must be explicit");
        String tab = range.substring(0, range.lastIndexOf('!')).strip();
        if (tab.startsWith("'") && tab.endsWith("'")) tab = tab.substring(1,tab.length()-1).replace("''", "'");
        if ("Cursos".equalsIgnoreCase(tab)) throw new IOException("Catalogue and import tabs must differ");
        validateEntries(entries);
    }
    static void validateEntries(List<CourseCatalogEntry> entries) throws IOException {
        var names = new HashSet<String>();
        var ids = new HashSet<Long>();
        for (var entry : entries) {
            String name = entry.name();
            if (name == null || name.isBlank() || name.contains("*") || name.contains("?") || name.contains("~")
                || entry.id() <= 0 || !ids.add(entry.id())
                || !names.add(name.replaceAll("(?U)\\s+", " ").strip().toLowerCase(Locale.ROOT)))
                throw new IOException("Catalogue contains ambiguous or invalid entries");
        }
    }
}
