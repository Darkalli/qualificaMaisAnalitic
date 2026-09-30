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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RegisterSheetMapperTests {
    private final RegisterSheetMapper mapper = new RegisterSheetMapper();

    private List<Object> header() {
        return new ArrayList<>(List.of("Nome completo", "Nome social", "CPF", "E-mail", "Rua", "Número",
                "Bairro", "Gênero", "Escolaridade", "Situação de trabalho", "Deficiência",
                "Curso de interesse", "Carimbo de data/hora", "Contato com WhatsApp",
                "O contato informado possui WhatsApp?", "Contato de familiar"));
    }

    private List<Object> row() {
        return new ArrayList<>(List.of("Pessoa Exemplo", "", "012.345.678-90", "pessoa@example.com",
                "Rua Exemplo", "42", "Centro", "Feminino", "Ensino Médio Completo",
                "Não, somente estudo", "Nenhuma", "42", "24/09/2026 13:45:10",
                "(11) 99999-0000", "Sim", "(11) 3333-0000"));
    }

    @Test
    void mapsEveryFieldAndPreservesLeadingCpfZero() {
        var result = mapper.map(List.of(header(), row()), 1);
        assertTrue(result.errors().isEmpty());
        var register = result.registers().getFirst();
        assertNull(register.getId());
        assertNotNull(register.getPerson());
        assertNull(register.getPerson().getId());
        assertEquals("Pessoa Exemplo", register.getPerson().getFullName());
        assertNull(register.getPerson().getSocialName());
        assertEquals("01234567890", register.getPerson().getCpf());
        assertEquals("pessoa@example.com", register.getPerson().getEmail());
        assertEquals("11999990000", register.getPerson().getPersonalPhone());
        assertEquals(Boolean.TRUE, register.getPerson().getPersonalPhoneHasWhatsapp());
        assertEquals("1133330000", register.getPerson().getFamilyPhone());
        assertNull(register.getPerson().getAddress().getId());
        assertEquals("Rua Exemplo", register.getPerson().getAddress().getStreet());
        assertEquals(42, register.getPerson().getAddress().getNumber());
        assertEquals("Centro", register.getPerson().getAddress().getNeighborhood());
        assertEquals(Gender.FEMALE, register.getPerson().getGender());
        assertEquals(Education.HIGH_SCHOOL_COMPLETE, register.getPerson().getEducation());
        assertEquals(WorkState.ONLY_STUDYING, register.getPerson().getWorkState());
        assertEquals(Set.of(Disabilities.NONE), register.getPerson().getDisabilities());
        assertEquals(42L, register.getCourseOfInterest().getId());
        assertNull(register.getCourseOfInterest().getName());
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
        assertEquals("Pessoa Exemplo", result.registers().getFirst().getPerson().getFullName());
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
                "neighborhood", "gender", "education", "workState", "disabilities", "courseOfInterest", "registerDate",
                "personalPhone", "personalPhoneHasWhatsapp", "familyPhone");
        assertEquals(1, mapper.map(List.of(header, row()), 1).registers().size());
    }

    @Test
    void acceptsCurrentFormHeadersAndPrefersRegistrationDateOverSubmissionTimestamp() {
        var header = header();
        var row = row();
        header.set(4, "Endereço (rua)");
        header.set(9, "Trabalha atualmente?");
        header.add("Data da inscrição");
        row.add("01/09/2026");
        var result = mapper.map(List.of(header, row), 1);
        assertTrue(result.errors().isEmpty());
        var register = result.registers().getFirst();
        assertEquals("Rua Exemplo", register.getPerson().getAddress().getStreet());
        assertEquals(WorkState.ONLY_STUDYING, register.getPerson().getWorkState());
        assertEquals(LocalDate.of(2026, 9, 1), register.getRegisterDate());

        Collections.reverse(header);
        Collections.reverse(row);
        assertEquals(LocalDate.of(2026, 9, 1),
                mapper.map(List.of(header, row), 1).registers().getFirst().getRegisterDate());
    }

    @Test
    void doesNotReplaceInvalidExplicitRegistrationDateWithSubmissionTimestamp() {
        var header = header();
        var row = row();
        header.add("Data da inscrição");
        row.add("31/02/2026");
        var result = mapper.map(List.of(header, row), 1);
        assertTrue(result.registers().isEmpty());
        assertTrue(result.errors().getFirst().message().contains("Data de cadastro"));
    }

    @Test
    void acceptsMissingSocialNameColumnAndTruncatedOptionalCell() {
        var header = header();
        var row = row();
        header.remove(1);
        row.remove(1);
        assertNull(mapper.map(List.of(header, row), 1).registers().getFirst().getPerson().getSocialName());
        header.add("Nome social");
        assertNull(mapper.map(List.of(header, row), 1).registers().getFirst().getPerson().getSocialName());
        row.add("Nome escolhido");
        assertEquals("Nome escolhido", mapper.map(List.of(header, row), 1).registers().getFirst().getPerson().getSocialName());
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
        var header = header();
        var row = row();
        header.add(header.remove(12));
        row.add(row.remove(12));
        row.removeLast();
        var result = mapper.map(List.of(header, row), 1);
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
    void acceptsPersonalPhoneWithoutWhatsappAndOptionalFamilyPhone() {
        for (Object answer : List.of("Não", "Nao", "false", false)) {
            var row = row();
            row.set(13, "(11) 3333-0000");
            row.set(14, answer);
            row.set(15, "");
            var result = mapper.map(List.of(header(), row), 1);
            assertTrue(result.errors().isEmpty());
            var register = result.registers().getFirst();
            assertEquals("1133330000", register.getPerson().getPersonalPhone());
            assertEquals(Boolean.FALSE, register.getPerson().getPersonalPhoneHasWhatsapp());
            assertNull(register.getPerson().getFamilyPhone());
        }
    }

    @Test
    void acceptsOmittedOrTruncatedFamilyPhone() {
        var header = header();
        var row = row();
        row.removeLast();
        assertNull(mapper.map(List.of(header, row), 1).registers().getFirst().getPerson().getFamilyPhone());
        header.removeLast();
        assertNull(mapper.map(List.of(header, row), 1).registers().getFirst().getPerson().getFamilyPhone());
    }

    @Test
    void normalizesBothPhoneMasksAndExplicitCountryCode() {
        var row = row();
        row.set(13, "+55 (11) 99999-0000");
        row.set(14, true);
        row.set(15, "+55 (21) 3333-0000");
        var register = mapper.map(List.of(header(), row), 1).registers().getFirst();
        assertEquals("11999990000", register.getPerson().getPersonalPhone());
        assertEquals(Boolean.TRUE, register.getPerson().getPersonalPhoneHasWhatsapp());
        assertEquals("2133330000", register.getPerson().getFamilyPhone());
    }

    @Test
    void rejectsMissingPersonalPhoneEvenWithoutWhatsappAndInvalidPhoneFormats() {
        for (String phone : List.of("", "99999-0000", "(11) telefone", "119999900001", "+1 11999990000", "01 99999-0000")) {
            var row = row();
            row.set(13, phone);
            row.set(14, "Não");
            var result = mapper.map(List.of(header(), row), 1);
            assertTrue(result.registers().isEmpty());
            assertTrue(result.errors().getFirst().message().contains("Telefone pessoal"));
        }
        var row = row();
        row.set(15, "3333-0000");
        var result = mapper.map(List.of(header(), row), 1);
        assertTrue(result.registers().isEmpty());
        assertTrue(result.errors().getFirst().message().contains("Contato de familiar"));
    }

    @Test
    void rejectsMissingOrUnknownWhatsappAnswerInsteadOfAssumingFalse() {
        for (String answer : List.of("", "talvez")) {
            var row = row();
            row.set(14, answer);
            var result = mapper.map(List.of(header(), row), 1);
            assertTrue(result.registers().isEmpty());
            assertTrue(result.errors().getFirst().message().contains("WhatsApp"));
        }
        var missingWhatsappHeader = header();
        missingWhatsappHeader.remove(14);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> mapper.map(List.of(missingWhatsappHeader), 1)).getMessage().contains("WhatsApp"));
        var missingPhoneHeader = header();
        missingPhoneHeader.remove(13);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> mapper.map(List.of(missingPhoneHeader), 1)).getMessage().contains("Telefone pessoal"));
    }

    @Test
    void acceptsMultipleDisabilitiesByDescriptionNameOrCode() {
        for (String value : List.of("Auditiva, Visual", "HEARING;VISUAL", "1, 4", "Auditiva\nVisual", "Auditiva\r\nVisual")) {
            var row = row();
            row.set(10, value);
            var result = mapper.map(List.of(header(), row), 1);
            assertTrue(result.errors().isEmpty(), value);
            assertEquals(Set.of(Disabilities.HEARING, Disabilities.VISUAL),
                    result.registers().getFirst().getPerson().getDisabilities());
        }
    }

    @Test
    void deduplicatesDisabilitiesAndKeepsMotorDescriptionIntact() {
        var row = row();
        row.set(10, "Intelectual, Auditiva, HEARING, 1, Física/Motora");
        var result = mapper.map(List.of(header(), row), 1);
        assertTrue(result.errors().isEmpty());
        assertEquals(Set.of(Disabilities.INTELLECTUAL, Disabilities.HEARING, Disabilities.MOTOR),
                result.registers().getFirst().getPerson().getDisabilities());
    }

    @Test
    void acceptsEachSingleDisabilityIncludingNoDeclaration() {
        for (Disabilities disability : Disabilities.values()) {
            var row = row();
            row.set(10, disability.getDescription());
            assertEquals(Set.of(disability), mapper.map(List.of(header(), row), 1)
                    .registers().getFirst().getPerson().getDisabilities());
        }
    }

    @Test
    void rejectsConflictingUnknownOrRemovedDisabilitiesWithoutImportingPartialRow() {
        for (String value : List.of("Nenhuma, Visual", "Sem Declaração, Auditiva", "Nenhuma, Sem Declaração",
                "Auditiva, desconhecida", "Múltiplas", "MULTIPLE", "2", "Auditiva,", "", "Auditiva;;Visual")) {
            var row = row();
            row.set(10, value);
            var result = mapper.map(List.of(header(), row, row()), 1);
            assertEquals(1, result.registers().size(), value);
            assertEquals(1, result.errors().size(), value);
            assertEquals(2, result.errors().getFirst().row());
            assertTrue(result.errors().getFirst().message().contains("Deficiência"));
        }
    }

    @Test
    void handlesEmptySheetsAndValidatesHeaderRow() {
        assertTrue(mapper.map(null, 1).registers().isEmpty());
        assertTrue(mapper.map(List.of(), 1).errors().isEmpty());
        assertTrue(mapper.map(List.of(header()), 1).registers().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> mapper.map(List.of(), 0));
    }

    @Test
    void acceptsExplicitCourseIdHeadersAndRejectsDuplicateAliases() {
        for (String title : List.of("ID do curso", "courseId", "courseOfInterestId")) {
            var header = header();
            header.set(11, title);
            var row = row();
            row.set(11, 42L);
            assertEquals(42L, mapper.map(List.of(header, row), 1).registers().getFirst().getCourseOfInterest().getId());
        }
        var duplicate = header();
        duplicate.add("ID do curso");
        assertThrows(IllegalArgumentException.class, () -> mapper.map(List.of(duplicate, row()), 1));
    }

    @Test
    void rejectsNamesInvalidIdsAndOverflowWithoutDiscardingOtherRows() {
        for (String value : List.of("Informática", "0", "-1", "1.5", "1.0", "1e3", "abc", "9223372036854775808", "")) {
            var invalid = row();
            invalid.set(11, value);
            var result = mapper.map(List.of(header(), invalid, row()), 5);
            assertEquals(1, result.registers().size(), value);
            assertEquals(1, result.errors().size(), value);
            assertEquals(6, result.errors().getFirst().row());
            assertTrue(result.errors().getFirst().message().contains("ID do curso"));
        }
    }
}
