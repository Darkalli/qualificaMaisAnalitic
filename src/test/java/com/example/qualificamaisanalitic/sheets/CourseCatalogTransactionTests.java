package com.example.qualificamaisanalitic.sheets;

import com.repositories.CourseRepository;
import com.sheets.*;
import com.sheets.config.SheetsProperties;
import com.services.CourseService;
import com.mappers.CourseMapper;
import com.dtos.CourseDtos.*;
import com.entities.Course;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseCatalogTransactionTests {
    @org.springframework.boot.test.context.TestConfiguration @EnableTransactionManagement
    static class Transactions {
        @Bean PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected boolean isExistingTransaction(Object transaction) { return TransactionSynchronizationManager.isActualTransactionActive(); }
                @Override protected void doBegin(Object transaction,TransactionDefinition definition) {}
                @Override protected void doCommit(DefaultTransactionStatus status) {}
                @Override protected void doRollback(DefaultTransactionStatus status) {}
            };
        }
    }
    @Test void crudInvalidationsAreCoalescedOnlyAfterCommitWithoutNetworkInTransaction() throws Exception {
        var repository = mock(CourseRepository.class); var writer = mock(GoogleCourseCatalogWriter.class);
        var p = new SheetsProperties(); p.setCatalogEnabled(true);
        when(repository.findAll()).thenReturn(List.of());
        var course = new Course(); course.setId(1L); course.setName("Java");
        when(repository.findByid(1L)).thenReturn(Optional.of(course));
        when(repository.findById(1L)).thenReturn(Optional.of(course));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Transactions.class);
            context.registerBean(SheetsProperties.class,() -> p);
            context.registerBean(CourseRepository.class,() -> repository);
            context.registerBean(GoogleCourseCatalogWriter.class,() -> writer);
            context.registerBean(CourseMapper.class,() -> org.mapstruct.factory.Mappers.getMapper(CourseMapper.class));
            context.register(CourseCatalogSync.class,CourseService.class); context.refresh();
            var sync = context.getBean(CourseCatalogSync.class); var service = context.getBean(CourseService.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            tx.executeWithoutResult(status -> {
                service.addCourse(new AddCourseDto("Java",null,LocalDate.of(2026,1,1),LocalDate.of(2026,1,1)));
                service.updateCourse(new UpdateCourseDto(1L,"Updated",null,null,null));
                service.deleteCourse(1L);
                sync.flushRequested(); verifyNoInteractions(writer);
            });
            verifyNoInteractions(writer);
            sync.flushRequested(); sync.flushRequested();
            verify(writer,times(1)).replace(List.of());
            reset(writer);
            tx.executeWithoutResult(status -> {
                service.addCourse(new AddCourseDto("Rolled back",null,null,null));
                status.setRollbackOnly();
            });
            sync.flushRequested(); verifyNoInteractions(writer);
            context.publishEvent(new CourseCatalogChanged());
            sync.flushRequested(); verifyNoInteractions(writer); // No transaction: no fallback execution.
        }
    }
}
