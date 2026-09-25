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
}
