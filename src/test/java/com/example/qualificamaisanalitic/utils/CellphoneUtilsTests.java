package com.example.qualificamaisanalitic.utils;

import com.utils.CellphoneUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CellphoneUtilsTests {
    @ParameterizedTest
    @CsvSource({
            "11999990000, 11999990000, (11) 99999-0000",
            "(11) 99999-0000, 11999990000, (11) 99999-0000",
            "+55 (11) 99999-0000, 11999990000, (11) 99999-0000",
            "5511999990000, 11999990000, (11) 99999-0000",
            "(11) 3333-4444, 1133334444, (11) 3333-4444",
            "+55 (11) 3333-4444, 1133334444, (11) 3333-4444",
            "55999990000, 55999990000, (55) 99999-0000",
            "5533334444, 5533334444, (55) 3333-4444",
            "+55 (55) 99999-0000, 55999990000, (55) 99999-0000",
            "551133334444, 1133334444, (11) 3333-4444"
    })
    void normalizesLandlinesMobilesAndCountryCodeWithoutRemovingDdd55(String input, String digits, String formatted) {
        assertEquals(digits, CellphoneUtils.cleanPhone(input));
        assertEquals(formatted, CellphoneUtils.formatPhone(input));
        assertEquals(digits, CellphoneUtils.cleanPhone(formatted));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void treatsMissingOptionalPhoneAsNull(String input) {
        assertNull(CellphoneUtils.formatPhone(input));
        assertNull(CellphoneUtils.cleanPhone(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "119999900", "123456789012", "+1 (212) 555-12345", "abc"})
    void rejectsUnsupportedLengthsInBothOperations(String input) {
        assertThrows(IllegalArgumentException.class, () -> CellphoneUtils.formatPhone(input));
        assertThrows(IllegalArgumentException.class, () -> CellphoneUtils.cleanPhone(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"11999990000abc", "abcdefghijk", "01 99999-0000", "+55 (01) 99999-0000",
            "+1 11999990000", "+11999990000", "+55999990000", "++55 (11) 99999-0000", "11/99999-0000"})
    void rejectsInvalidCharactersDddAndInternationalPrefix(String input) {
        assertThrows(IllegalArgumentException.class, () -> CellphoneUtils.cleanPhone(input));
        assertThrows(IllegalArgumentException.class, () -> CellphoneUtils.formatPhone(input));
    }
}
