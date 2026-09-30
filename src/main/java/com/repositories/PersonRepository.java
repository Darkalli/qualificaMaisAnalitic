package com.repositories;

import com.entities.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {
    Optional<Person> findByCpf(String cpf);

    Optional<Person> getById(long id);

    @Query("select p from Person p order by p.fullName asc, p.id asc")
    List<Person> getAll();
}
