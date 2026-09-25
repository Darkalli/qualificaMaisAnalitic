package com.sheets.services;

import com.sheets.GoogleSheetsReader;
import com.sheets.RegisterSheetMapper;
import com.sheets.SheetImportResult;
import com.sheets.config.SheetsProperties;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
public class RegisterCollectionService {
    private final GoogleSheetsReader reader;
    private final RegisterSheetMapper mapper;
    private final SheetsProperties properties;

    public RegisterCollectionService(GoogleSheetsReader reader, RegisterSheetMapper mapper,
                                     SheetsProperties properties) {
        this.reader = reader;
        this.mapper = mapper;
        this.properties = properties;
    }

    public SheetImportResult collect() throws IOException, GeneralSecurityException {
        return mapper.map(reader.read(), properties.getHeaderRow());
    }
}
