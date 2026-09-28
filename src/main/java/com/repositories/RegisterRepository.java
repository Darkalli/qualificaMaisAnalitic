package com.repositories;

import com.entities.Register;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RegisterRepository extends JpaRepository<Register, Long> {
    Optional<Register> findByPerson_CpfAndCourseOfInterest(String cpf, String courseOfInterest);
}
