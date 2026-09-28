package com.sheets;

import com.entities.Register;
import java.util.List;

public record SheetImportResult(List<Register> registers, List<RowError> errors, int ignoredRows) {
    public SheetImportResult {
        registers = List.copyOf(registers);
        errors = List.copyOf(errors);
    }

    public record RowError(int row, String message) { }
}
