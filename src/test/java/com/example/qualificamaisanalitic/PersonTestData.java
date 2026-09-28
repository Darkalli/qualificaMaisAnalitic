package com.example.qualificamaisanalitic;

import com.entities.Person;

public final class PersonTestData {
    private PersonTestData() { }

    public static Person person(String cpf) {
        return RegisterTestData.register(cpf).getPerson();
    }
}
