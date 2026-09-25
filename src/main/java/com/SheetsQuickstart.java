package com;

import com.sheets.GoogleSheetsReader;
import com.sheets.services.RegisterCollectionService;
import com.sheets.RegisterSheetMapper;
import com.sheets.config.SheetsProperties;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

/** Executa a coleta sem iniciar o Spring ou conectar ao PostgreSQL. */
public class SheetsQuickstart {
    public static void main(String... args) throws Exception {
        if (args.length > 3) {
            throw new IllegalArgumentException("Uso: SheetsQuickstart [spreadsheetId] [intervalo com cabecalho] [linha do cabecalho]");
        }
        var properties = loadProperties(new StandardEnvironment());
        if (args.length >= 1) {
            properties.setSpreadsheetId(args[0]);
        }
        if (args.length >= 2) {
            properties.setRange(args[1]);
        }
        if (args.length == 3) {
            properties.setHeaderRow(Integer.parseInt(args[2]));
        }
        var service = new RegisterCollectionService(new GoogleSheetsReader(properties),
                new RegisterSheetMapper(), properties);
        var result = service.collect();
        System.out.printf("Cadastros validos: %d | Linhas com erro: %d | Linhas vazias: %d%n",
                result.registers().size(), result.errors().size(), result.ignoredRows());
        result.errors().forEach(error -> System.out.printf("Linha %d: %s%n", error.row(), error.message()));
    }

    static SheetsProperties loadProperties(ConfigurableEnvironment environment) {
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return Binder.get(environment).bind("app.sheets", SheetsProperties.class)
                .orElseGet(SheetsProperties::new);
    }
}
