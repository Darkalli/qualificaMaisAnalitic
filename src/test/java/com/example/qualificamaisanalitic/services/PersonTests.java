package com.example.qualificamaisanalitic.services;

import com.dtos.personDtos.UpdatePersonDto;
import com.entities.Person;
import com.enums.Disabilities;
import com.mappers.PersonMapper;
import com.repositories.PersonRepository;
import com.services.PersonService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

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
        assertEquals(dto.disabilities(), saved.getDisabilities());
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
        verify(repository).save(person);
    }

    @Test
    void updatesAndNormalizesPhonesAndReplacesDisabilities() {
        var person = ServiceTestData.person();
        when(repository.findByCpf("01234567890")).thenReturn(Optional.of(person));
        service.updatePerson(new UpdatePersonDto("012.345.678-90", "Outro nome", null,
                "+55 (21) 98888-7777", "(21) 2222-3333", null, null, null, null,
                Set.of(Disabilities.MOTOR)));
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
    void normalizesCpfWhenSearchingAndReturnsEmptyForUnknownPerson() {
        var person = ServiceTestData.person();
        when(repository.findByCpf("01234567890")).thenReturn(Optional.of(person));
        when(repository.findByCpf("98765432100")).thenReturn(Optional.empty());
        assertSame(person, service.getByCpf("012.345.678-90").orElseThrow());
        assertTrue(service.getByCpf("987.654.321-00").isEmpty());
    }

    @Test
    void listsPeopleSortedByRequestedField() {
        var people = List.of(ServiceTestData.person());
        when(repository.findAll(Sort.by(Sort.Direction.ASC, "fullName"))).thenReturn(people);
        assertEquals(people, service.getAllPerson());
    }

    @Test
    void deletesTheRequestedPerson() {
        var person = ServiceTestData.person();
        when(repository.getById(Long.valueOf(7))).thenReturn(person);
        service.deletePerson(7L);
        verify(repository).delete(person);
    }
}
