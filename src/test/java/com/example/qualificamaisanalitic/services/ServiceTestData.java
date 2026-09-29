package com.example.qualificamaisanalitic.services;

import com.dtos.personDtos.AddPersonDto;
import com.entities.Person;
import com.example.qualificamaisanalitic.PersonTestData;

final class ServiceTestData {
    private ServiceTestData() { }

    static Person person() {
        var person = PersonTestData.person("01234567890");
        person.setId(7L);
        person.setSocialName("Nome social");
        person.setFamilyPhone("1133334444");
        return person;
    }

    static AddPersonDto addPerson(String cpf, String phone, String familyPhone) {
        var person = person();
        return new AddPersonDto(person.getFullName(), person.getSocialName(), cpf, person.getEmail(),
                phone, false, familyPhone, person.getAddress(), person.getGender(), person.getEducation(),
                person.getWorkState(), person.getDisabilities());
    }
}
