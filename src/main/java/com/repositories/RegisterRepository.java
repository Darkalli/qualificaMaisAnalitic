package com.repositories;

import com.entities.Register;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegisterRepository extends JpaRepository<Register, Long> {
    Optional<Register> findByPerson_CpfAndCourseOfInterest_Id(String cpf, Long courseId);

    List<Register> findByPerson_Cpf(String cpf);
}
