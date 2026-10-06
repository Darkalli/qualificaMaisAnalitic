package com.sheets.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.sheets")
public class SheetsProperties {
    private String spreadsheetId = "";
    // O intervalo deve começar na linha do cabeçalho.
    private String range = "";
    private int headerRow = 1;
    private String credentialsPath = "classpath:credentials.json";
    private String tokensDirectory = "tokens";
    private int oauthPort = 8888;
    private boolean catalogEnabled = false;
    private String catalogSheetName = "Cursos";
    private long catalogIntervalMs = 300000;
    private long catalogInitialDelayMs = 10000;
    private long catalogDispatchIntervalMs = 1000;
    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 30000;
}
