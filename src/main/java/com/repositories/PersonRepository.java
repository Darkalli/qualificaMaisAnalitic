package com.repositories;

import com.entities.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {
    Optional<Person> findByCpf(String cpf);

    Optional<Person> getById(long id);

    List<Person> getAll();
}
