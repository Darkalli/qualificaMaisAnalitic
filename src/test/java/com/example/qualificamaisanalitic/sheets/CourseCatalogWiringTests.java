package com.example.qualificamaisanalitic.sheets;

import com.repositories.CourseRepository;
import com.sheets.*;
import com.sheets.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CourseCatalogWiringTests {
    @Test void startupAndPeriodicSchedulerReconcileAndRetryOnManagedWorker() throws Exception {
        var repository = mock(CourseRepository.class); var writer = mock(GoogleCourseCatalogWriter.class);
        when(repository.findAll()).thenReturn(java.util.List.of());
        var completed = new java.util.concurrent.CountDownLatch(2);
        var failures = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            completed.countDown();
            if (failures.getAndIncrement() == 0) throw new java.io.IOException("test failure");
            return null;
        }).when(writer).replace(anyList());
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",java.util.Map.of(
                "app.sheets.catalog-enabled","true", "app.sheets.catalog-initial-delay-ms","10",
                "app.sheets.catalog-interval-ms","20", "app.sheets.catalog-dispatch-interval-ms","20")));
            context.register(com.sheets.config.SheetsConfiguration.class,CourseCatalogSync.class);
            context.registerBean(CourseRepository.class,() -> repository);
            context.registerBean(GoogleCourseCatalogWriter.class,() -> writer);
            context.refresh();
            context.publishEvent(new org.springframework.boot.context.event.ApplicationReadyEvent(
                new org.springframework.boot.SpringApplication(),new String[0],context,java.time.Duration.ZERO));
            assertTrue(completed.await(2,java.util.concurrent.TimeUnit.SECONDS));
        }
        verify(writer,atLeast(2)).replace(java.util.List.of());
    }
    @Test void disabledDefaultStartsCompleteBeanGraphWithoutCredentialsDatabaseOrGoogle() {
        var repository = mock(CourseRepository.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SheetsProperties.class,SheetsProperties::new);
            context.registerBean(CourseRepository.class,() -> repository);
            context.register(GoogleSheetsClientProvider.class,GoogleSheetsReader.class,GoogleCourseCatalogWriter.class,CourseCatalogSync.class);
            context.refresh();
            context.getBean(CourseCatalogSync.class).reconcile();
            assertNotNull(context.getBean(GoogleCourseCatalogWriter.class));
            verifyNoInteractions(repository);
        }
    }
}
