package com.utils;

import com.entities.Course;
import com.entities.CourseClass;
import com.entities.Register;
import com.repositories.RegisterRepository;

import java.util.List;

public class RegisterUtils {
    public static boolean hasScheduleConflict(RegisterRepository registerRepository, Register newRegister) {
        Course newCourse = newRegister.getCourseOfInterest();

        List<Register> registers =
                registerRepository.findByPerson_Cpf(
                        newRegister.getPerson().getCpf()
                );

        for (Register register : registers) {
            if (newRegister.getId() != null && newRegister.getId().equals(register.getId())) {
                continue;
            }
            Course course = register.getCourseOfInterest();
            for (CourseClass courseClass : course.getCourseClass()) {
                for (CourseClass newCourseClass : newCourse.getCourseClass()) {
                    boolean sameDay =
                            newCourseClass.getDay().equals(courseClass.getDay());
                    boolean conflict =
                            sameDay
                                    && newCourseClass.getStart()
                                    .isBefore(courseClass.getFinish())
                                    && newCourseClass.getFinish()
                                    .isAfter(courseClass.getStart());
                    if (conflict) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
