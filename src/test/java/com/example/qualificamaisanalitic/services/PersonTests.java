package com.example.qualificamaisanalitic.services;

import com.dtos.personDtos.UpdatePersonDto;
import com.dtos.personDtos.AddPersonDto;
import com.entities.Person;
import com.enums.Disabilities;
import com.mappers.PersonMapper;
import com.repositories.PersonRepository;
import com.services.PersonService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonTests {
    @Mock private PersonRepository repository;
    private PersonService service;

    @BeforeEach
    void setUp() {
        service = new PersonService(repository, Mappers.getMapper(PersonMapper.class));
    }

    @Test
    void createsCompletePersonWithNormalizedCpfAndBothPhones() {
        var dto = ServiceTestData.addPerson("012.345.678-90", "+55 (11) 99999-0000", "(11) 3333-4444");
        service.addPerson(dto);
        var capture = ArgumentCaptor.forClass(Person.class);
        verify(repository).save(capture.capture());
        var saved = capture.getValue();
        assertNull(saved.getId());
        assertEquals("01234567890", saved.getCpf());
        assertEquals("11999990000", saved.getPersonalPhone());
        assertEquals("1133334444", saved.getFamilyPhone());
        assertEquals(false, saved.getPersonalPhoneHasWhatsapp());
        assertEquals(dto.fullName(), saved.getFullName());
        assertEquals(dto.socialName(), saved.getSocialName());
        assertEquals(dto.email(), saved.getEmail());
        assertSame(dto.address(), saved.getAddress());
        assertEquals(dto.gender(), saved.getGender());
        assertEquals(dto.education(), saved.getEducation());
        assertEquals(dto.workState(), saved.getWorkState());
        assertEquals(ServiceTestData.person().getDisabilities(), saved.getDisabilities());
    }

    @Test
    void allowsMissingFamilyPhone() {
        service.addPerson(ServiceTestData.addPerson("01234567890", "11999990000", null));
        verify(repository).save(argThat(person -> person.getFamilyPhone() == null));
    }

    @Test
    void rejectsMalformedCpfBeforeSaving() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addPerson(ServiceTestData.addPerson("123", "11999990000", null)));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsInvalidFamilyPhoneWithoutSavingPartialPerson() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addPerson(ServiceTestData.addPerson("01234567890", "11999990000", "123")));
        verifyNoInteractions(repository);
    }

    @Test
    void partialUpdatePreservesPhonesAndOtherOmittedFields() {
        var person = ServiceTestData.person();
        var originalDisabilities = Set.copyOf(person.getDisabilities());
        when(repository.findByCpf("01234567890")).thenReturn(Optional.of(person));
        service.updatePerson(new UpdatePersonDto("012.345.678-90", null, "novo@example.com",
                null, null, null, null, null, null, null));
        assertEquals("novo@example.com", person.getEmail());
        assertEquals("11999990000", person.getPersonalPhone());
        assertEquals("1133334444", person.getFamilyPhone());
        assertEquals("Nome social", person.getSocialName());
        assertEquals("Pessoa Exemplo", person.getFullName());
        assertEquals(false, person.getPersonalPhoneHasWhatsapp());
        assertEquals(7L, person.getId());
        assertEquals(originalDisabilities, person.getDisabilities());
        verify(repository).save(person);
    }

    @Test
    void updatesAndNormalizesPhonesAndReplacesDisabilities() {
        var person = ServiceTestData.person();
        when(repository.findByCpf("01234567890")).thenReturn(Optional.of(person));
        service.updatePerson(new UpdatePersonDto("012.345.678-90", "Outro nome", null,
                "+55 (21) 98888-7777", "(21) 2222-3333", null, null, null, null,
                Set.of("Física/Motora")));
        assertEquals("01234567890", person.getCpf());
        assertEquals("21988887777", person.getPersonalPhone());
        assertEquals("2122223333", person.getFamilyPhone());
        assertEquals("Outro nome", person.getSocialName());
        assertEquals(Set.of(Disabilities.MOTOR), person.getDisabilities());
        verify(repository).save(person);
    }

    @Test
    void refusesUpdateOfUnknownPerson() {
        when(repository.findByCpf("01234567890")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.updatePerson(new UpdatePersonDto(
                "01234567890", null, "novo@example.com", null, null, null, null, null, null, null)));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidUpdateBeforeChangingExistingFields() {
        var person = ServiceTestData.person();
        when(repository.findByCpf("01234567890")).thenReturn(Optional.of(person));
        assertThrows(IllegalArgumentException.class, () -> service.updatePerson(new UpdatePersonDto(
                "01234567890", "Outro nome", "novo@example.com", "123", null, null, null, null, null, null)));
        assertEquals("Nome social", person.getSocialName());
        assertEquals("pessoa@example.com", person.getEmail());
        assertEquals("11999990000", person.getPersonalPhone());
        verify(repository, never()).save(any());
    }

    @Test
    void explicitlyBlankFamilyPhoneClearsOnlyTheOptionalContact() {
        var person = ServiceTestData.person();
        when(repository.findByCpf("01234567890")).thenReturn(Optional.of(person));
        service.updatePerson(new UpdatePersonDto("01234567890", null, null,
                null, "   ", null, null, null, null, null));
        assertNull(person.getFamilyPhone());
        assertEquals("11999990000", person.getPersonalPhone());
        verify(repository).save(person);
    }

    @Test
    void normalizesCpfWhenSearchingAndReportsUnknownPerson() {
        var person = ServiceTestData.person();
        when(repository.findByCpf("01234567890")).thenReturn(Optional.of(person));
        when(repository.findByCpf("98765432100")).thenReturn(Optional.empty());
        assertSame(person, service.getByCpf("012.345.678-90"));
        assertThrows(EntityNotFoundException.class, () -> service.getByCpf("987.654.321-00"));
    }

    @Test
    void listsPeopleFromRepository() {
        var people = List.of(ServiceTestData.person());
        when(repository.getAll()).thenReturn(people);
        assertEquals(people, service.getAllPerson());
    }

    @Test
    void deletesTheRequestedPerson() {
        var person = ServiceTestData.person();
        when(repository.getById(Long.valueOf(7))).thenReturn(person);
        service.deletePerson(7L);
        verify(repository).delete(person);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Auditiva;Visual", "HEARING;VISUAL", "1;4", "Auditiva;HEARING;1;Visual"})
    void createsPersonUsingSharedDisabilityConversion(String input) {
        service.addPerson(withDisabilities(Set.of(input.split(";"))));
        verify(repository).save(argThat(person -> person.getDisabilities()
                .equals(Set.of(Disabilities.HEARING, Disabilities.VISUAL))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nenhuma;Visual", "Sem Declaração;Auditiva", "NONE;NO_DECLARATION", "desconhecida"})
    void rejectsInvalidDisabilitiesBeforeCreatingPerson(String input) {
        assertThrows(IllegalArgumentException.class,
                () -> service.addPerson(withDisabilities(Set.of(input.split(";")))));
        verifyNoInteractions(repository);
    }

    @Test
    void allowsMissingDisabilitiesAccordingToSharedProcessor() {
        service.addPerson(withDisabilities(null));
        verify(repository).save(argThat(person -> person.getDisabilities().isEmpty()));
    }

    @Test
    void rejectsConflictingDisabilitiesBeforeApplyingAnyPatchFields() {
        var person = ServiceTestData.person();
        var originalDisabilities = Set.copyOf(person.getDisabilities());
        when(repository.findByCpf("01234567890")).thenReturn(Optional.of(person));
        assertThrows(IllegalArgumentException.class, () -> service.updatePerson(new UpdatePersonDto(
                "01234567890", "Não deve mudar", "novo@example.com", null, null, null,
                null, null, null, Set.of("Nenhuma", "Visual"))));
        assertEquals("Nome social", person.getSocialName());
        assertEquals("pessoa@example.com", person.getEmail());
        assertEquals(originalDisabilities, person.getDisabilities());
        verify(repository, never()).save(any());
    }

    @Test
    void explicitlyEmptyDisabilitiesClearsTheCollection() {
        var person = ServiceTestData.person();
        when(repository.findByCpf("01234567890")).thenReturn(Optional.of(person));
        service.updatePerson(new UpdatePersonDto("01234567890", null, null, null,
                null, null, null, null, null, Set.of()));
        assertTrue(person.getDisabilities().isEmpty());
        verify(repository).save(person);
    }

    private AddPersonDto withDisabilities(Set<String> disabilities) {
        var dto = ServiceTestData.addPerson("01234567890", "11999990000", null);
        return new AddPersonDto(dto.fullName(), dto.socialName(), dto.cpf(), dto.email(),
                dto.personalPhone(), dto.personalPhoneHasWhatsapp(), dto.familyPhone(), dto.address(),
                dto.gender(), dto.education(), dto.workState(), disabilities);
    }
}
