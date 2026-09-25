package com.sheets;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.sheets.config.SheetsProperties;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class GoogleSheetsReader {
    private final SheetsProperties properties;

    public GoogleSheetsReader(SheetsProperties properties) {
        this.properties = properties;
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
        var transport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = GsonFactory.getDefaultInstance();
        var resource = new DefaultResourceLoader().getResource(properties.getCredentialsPath());
        GoogleClientSecrets secrets;
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            secrets = GoogleClientSecrets.load(jsonFactory, reader);
        }
        var flow = new GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory, secrets,
                List.of(SheetsScopes.SPREADSHEETS_READONLY))
                .setDataStoreFactory(new FileDataStoreFactory(new File(properties.getTokensDirectory())))
                .setAccessType("offline")
                .build();
        var receiver = new LocalServerReceiver.Builder().setPort(properties.getOauthPort()).build();
        var credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        return new Sheets.Builder(transport, jsonFactory, credential)
                .setApplicationName("Qualifica Mais Analitic").build();
    }
}
