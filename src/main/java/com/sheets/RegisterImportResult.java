package com.sheets;

import java.util.List;

/** Resumo sem os valores pessoais importados ou divergentes. */
public record RegisterImportResult(int inserted, int unchanged, List<Conflict> conflicts,
                                   List<SheetImportResult.RowError> errors, int ignoredRows) {
    public RegisterImportResult {
        conflicts = List.copyOf(conflicts);
        errors = List.copyOf(errors);
    }

    public record Conflict(Long registerId, List<String> fields) {
        public Conflict {
            fields = List.copyOf(fields);
        }
    }
}
