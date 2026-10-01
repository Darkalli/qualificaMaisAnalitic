package com.example.qualificamaisanalitic.utils;

import com.utils.CpfUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CpfUtilsTests {
    @ParameterizedTest
    @ValueSource(strings = {"01234567890", "012.345.678-90", "01234567890abc", "abc 012.345.678-90"})
    void formatsAndCleansCpfWithoutLosingLeadingZero(String input) {
        assertEquals("012.345.678-90", CpfUtils.formatCpf(input));
        assertEquals("01234567890", CpfUtils.cleanCpf(input));
        assertEquals("01234567890", CpfUtils.cleanCpf(CpfUtils.formatCpf(input)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "1234567890", "123456789012", "abc", "1234567890abc", "012345678901abc"})
    void rejectsIncorrectNumberOfDigitsWhenFormatting(String input) {
        assertThrows(IllegalArgumentException.class, () -> CpfUtils.formatCpf(input));
    }

    @Test
    void preservesNullForCallersToHandleRequiredFields() {
        assertNull(CpfUtils.formatCpf(null));
        assertNull(CpfUtils.cleanCpf(null));
    }
}
