package com.repositories;

import com.entities.Course;
import com.entities.Person;
import com.entities.Register;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RegisterRepository extends JpaRepository<Register, Long> {

    Optional<Register> findByPerson_CpfAndCourseOfInterest_Id(String cpf, Long courseId);

    List<Register> findByPerson_Cpf(String cpf);

    boolean existsByPersonAndCourseOfInterest(Person person, Course course);

    @Query("select distinct r.person.cpf from Register r where r.courseOfInterest.id = :courseId and r.status = com.enums.StatusRegister.ACTIVE order by r.person.cpf")
    List<String> findPersonCpfsByCourseId(Long courseId);
}
