package com;

import com.sheets.GoogleSheetsReader;
import com.sheets.RegisterCollectionService;
import com.sheets.RegisterSheetMapper;
import com.sheets.SheetsProperties;

/** Executa a coleta sem iniciar o Spring ou conectar ao PostgreSQL. */
public class SheetsQuickstart {
    public static void main(String... args) throws Exception {
        if (args.length < 1 || args.length > 3) {
            throw new IllegalArgumentException("Uso: SheetsQuickstart <spreadsheetId> [intervalo com cabecalho] [linha do cabecalho]");
        }
        var properties = new SheetsProperties();
        properties.setSpreadsheetId(args[0]);
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
}
