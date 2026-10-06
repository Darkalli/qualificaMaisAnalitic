package com.sheets;

import com.google.api.services.sheets.v4.Sheets;
import com.sheets.config.SheetsProperties;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleSheetsReader {
    private final SheetsProperties properties;
    private final GoogleSheetsClientProvider provider;

    public GoogleSheetsReader(SheetsProperties properties) {
        this(properties, new GoogleSheetsClientProvider(properties));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleSheetsReader(SheetsProperties properties, GoogleSheetsClientProvider provider) {
        this.properties = properties;
        this.provider = provider;
    }

    public List<List<Object>> read() throws IOException, GeneralSecurityException {
        if (properties.getSpreadsheetId() == null || properties.getSpreadsheetId().isBlank()) {
            throw new IllegalArgumentException("Configure app.sheets.spreadsheet-id antes de coletar os dados.");
        }
        if (properties.getRange() == null || properties.getRange().isBlank()) {
            throw new IllegalArgumentException("Configure app.sheets.range incluindo o cabeçalho.");
        }
        if (properties.getHeaderRow() < 1) {
            throw new IllegalArgumentException("app.sheets.header-row deve ser maior que zero.");
        }
        var values = createClient().spreadsheets().values()
                .get(properties.getSpreadsheetId(), properties.getRange())
                .setMajorDimension("ROWS")
                .setValueRenderOption("FORMATTED_VALUE")
                .execute().getValues();
        return values == null ? List.of() : values;
    }

    private Sheets createClient() throws IOException, GeneralSecurityException {
        return provider.client();
    }
}
