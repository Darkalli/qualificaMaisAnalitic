package com.repositories;

import com.entities.Person;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByCpf(String cpf);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Person p where p.cpf = :cpf")
    Optional<Person> findByCpfForUpdate(String cpf);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Person p where p.id = :id")
    Optional<Person> findByIdForUpdate(Long id);

    @Query("select p from Person p order by p.fullName asc, p.id asc")
    List<Person> getAll();
}
