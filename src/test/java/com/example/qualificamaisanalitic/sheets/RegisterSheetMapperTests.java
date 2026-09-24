package com.example.qualificamaisanalitic.sheets;

import com.enums.Disabilities;
import com.enums.Education;
import com.enums.Gender;
import com.enums.WorkState;
import com.sheets.RegisterSheetMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegisterSheetMapperTests {
    private final RegisterSheetMapper mapper = new RegisterSheetMapper();

    private List<Object> header() {
        return new ArrayList<>(List.of("Nome completo", "Nome social", "CPF", "E-mail", "Rua", "Número",
                "Bairro", "Gênero", "Escolaridade", "Situação de trabalho", "Deficiência",
                "Curso de interesse", "Carimbo de data/hora"));
    }

    private List<Object> row() {
        return new ArrayList<>(List.of("Pessoa Exemplo", "", "012.345.678-90", "pessoa@example.com",
                "Rua Exemplo", "42", "Centro", "Feminino", "Ensino Médio Completo",
                "Não, somente estudo", "Nenhuma", "Informática", "24/09/2026 13:45:10"));
    }

    @Test
    void mapsEveryFieldAndPreservesLeadingCpfZero() {
        var result = mapper.map(List.of(header(), row()), 1);
        assertTrue(result.errors().isEmpty());
        var register = result.registers().getFirst();
        assertNull(register.getId());
        assertEquals("Pessoa Exemplo", register.getFullName());
        assertNull(register.getSocialName());
        assertEquals("01234567890", register.getCpf());
        assertEquals("pessoa@example.com", register.getEmail());
        assertNull(register.getAddress().getId());
        assertEquals("Rua Exemplo", register.getAddress().getStreet());
        assertEquals(42, register.getAddress().getNumber());
        assertEquals("Centro", register.getAddress().getNeighborhood());
        assertEquals(Gender.FEMALE, register.getGender());
        assertEquals(Education.HIGH_SCHOOL_COMPLETE, register.getEducation());
        assertEquals(WorkState.ONLY_STUDYING, register.getWorkState());
        assertEquals(Disabilities.NONE, register.getDisabilities());
        assertEquals("Informática", register.getCourseOfInterest());
        assertEquals(LocalDate.of(2026, 9, 24), register.getRegisterDate());
    }

    @Test
    void acceptsReorderedColumnsAndIgnoresExtraColumn() {
        var header = header();
        var row = row();
        Collections.reverse(header);
        Collections.reverse(row);
        header.add("Observações");
        row.add("Informação adicional");
        var result = mapper.map(List.of(header, row), 1);
        assertEquals("Pessoa Exemplo", result.registers().getFirst().getFullName());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void acceptsEnumCodesNamesAndUnaccentedDescriptions() {
        var row = row();
        row.set(7, 1);
        row.set(8, "HIGH_SCHOOL_COMPLETE");
        row.set(9, "Nao, somente estudo");
        row.set(10, "NONE");
        assertEquals(1, mapper.map(List.of(header(), row), 1).registers().size());
    }

    @Test
    void acceptsJavaFieldNamesAsHeaders() {
        List<Object> header = List.of("fullName", "socialName", "cpf", "email", "street", "number",
                "neighborhood", "gender", "education", "workState", "disabilities", "courseOfInterest", "registerDate");
        assertEquals(1, mapper.map(List.of(header, row()), 1).registers().size());
    }

    @Test
    void acceptsMissingSocialNameColumnAndTruncatedOptionalCell() {
        var header = header();
        var row = row();
        header.remove(1);
        row.remove(1);
        assertNull(mapper.map(List.of(header, row), 1).registers().getFirst().getSocialName());
        header.add("Nome social");
        assertNull(mapper.map(List.of(header, row), 1).registers().getFirst().getSocialName());
        row.add("Nome escolhido");
        assertEquals("Nome escolhido", mapper.map(List.of(header, row), 1).registers().getFirst().getSocialName());
    }

    @Test
    void reportsMissingAndDuplicateHeadersBeforeProcessingRows() {
        var missing = assertThrows(IllegalArgumentException.class,
                () -> mapper.map(List.of(List.of("CPF"), row()), 1));
        assertTrue(missing.getMessage().contains("Nome completo"));
        var header = header();
        header.add("fullName");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> mapper.map(List.of(header, row()), 1)).getMessage().contains("duplicada"));
    }

    @Test
    void continuesAfterInvalidRowsAndReportsActualSheetRow() {
        var invalid = row();
        invalid.set(7, "inválido");
        var result = mapper.map(List.of(header(), List.of(), invalid, row()), 5);
        assertEquals(1, result.registers().size());
        assertEquals(1, result.ignoredRows());
        assertEquals(1, result.errors().size());
        assertEquals(7, result.errors().getFirst().row());
        assertTrue(result.errors().getFirst().message().contains("Gênero"));
        assertFalse(result.errors().getFirst().message().contains("Pessoa Exemplo"));
    }

    @Test
    void reportsTruncatedRequiredCellInsteadOfIndexError() {
        var row = row();
        row.removeLast();
        var result = mapper.map(List.of(header(), row), 1);
        assertTrue(result.registers().isEmpty());
        assertTrue(result.errors().getFirst().message().contains("Data de cadastro"));
    }

    @Test
    void rejectsImpossibleDatesInsteadOfAdjustingThem() {
        for (String date : List.of("31/02/2026", "29/02/2025 10:00:00", "2026-02-30", "24/09/2026 25:00:00")) {
            var row = row();
            row.set(12, date);
            var result = mapper.map(List.of(header(), row), 1);
            assertTrue(result.registers().isEmpty(), date);
            assertTrue(result.errors().getFirst().message().contains("Data de cadastro"));
        }
    }

    @Test
    void acceptsBrazilianAndIsoDates() {
        for (String date : List.of("24/09/2026", "2026-09-24", "2026-09-24T13:45:10")) {
            var row = row();
            row.set(12, date);
            assertEquals(LocalDate.of(2026, 9, 24),
                    mapper.map(List.of(header(), row), 1).registers().getFirst().getRegisterDate());
        }
    }

    @Test
    void rejectsMalformedCpfAndNonNumericAddressNumber() {
        for (Object cpf : List.of("1234567890", "01234567890abc", "")) {
            var row = row();
            row.set(2, cpf);
            assertTrue(mapper.map(List.of(header(), row), 1).errors().getFirst().message().contains("CPF"));
        }
        for (Object number : List.of("s/n", "-1", "2147483648", "1.5")) {
            var row = row();
            row.set(5, number);
            assertTrue(mapper.map(List.of(header(), row), 1).errors().getFirst().message().contains("Número"));
        }
    }

    @Test
    void handlesEmptySheetsAndValidatesHeaderRow() {
        assertTrue(mapper.map(null, 1).registers().isEmpty());
        assertTrue(mapper.map(List.of(), 1).errors().isEmpty());
        assertTrue(mapper.map(List.of(header()), 1).registers().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> mapper.map(List.of(), 0));
    }
}
