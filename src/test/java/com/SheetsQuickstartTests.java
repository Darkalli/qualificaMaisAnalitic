package com;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SheetsQuickstartTests {
    @TempDir
    Path directory;

    @Test
    void loadsLocalPropertiesAndResolvesPlaceholdersWithoutStartingSpring() throws Exception {
        Path config = directory.resolve("application.properties");
        Files.writeString(config, """
                app.sheets.spreadsheet-id=${TEST_SHEET_ID:example-sheet}
                app.sheets.range='Inscri\\u00e7\\u00f5es de exemplo'!A5:Z
                app.sheets.header-row=5
                app.sheets.oauth-port=9999
                """);
        var environment = new MockEnvironment()
                .withProperty("spring.config.location", config.toUri().toString())
                .withProperty("TEST_SHEET_ID", "test-sheet");
        var properties = SheetsQuickstart.loadProperties(environment);
        assertEquals("test-sheet", properties.getSpreadsheetId());
        assertEquals("'Inscrições de exemplo'!A5:Z", properties.getRange());
        assertEquals(5, properties.getHeaderRow());
        assertEquals(9999, properties.getOauthPort());
    }

    @Test
    void hasNoPrivateSpreadsheetDefaultsWhenConfigurationIsAbsent() {
        var environment = new MockEnvironment().withProperty("spring.config.location",
                "optional:" + directory.resolve("missing.properties").toUri());
        var properties = SheetsQuickstart.loadProperties(environment);
        assertEquals("", properties.getSpreadsheetId());
        assertEquals("", properties.getRange());
    }
}
