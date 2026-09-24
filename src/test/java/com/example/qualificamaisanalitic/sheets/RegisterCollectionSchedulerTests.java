package com.example.qualificamaisanalitic.sheets;

import com.sheets.RegisterCollectionScheduler;
import com.sheets.RegisterCollectionService;
import com.sheets.SheetImportResult;
import com.sheets.SheetsConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.FixedDelayTask;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegisterCollectionSchedulerTests {
    private final RegisterCollectionService service = mock(RegisterCollectionService.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SheetsConfiguration.class, RegisterCollectionScheduler.class)
            .withBean(RegisterCollectionService.class, () -> service);

    @Test
    void usesConfiguredDelayBetweenChecksAndBeforeFirstCheck() {
        contextRunner.withPropertyValues("app.sheets.check-interval=2m", "app.sheets.check-initial-delay=30s")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
                    assertEquals(1, tasks.size());
                    var task = assertInstanceOf(FixedDelayTask.class, tasks.iterator().next().getTask());
                    assertEquals(Duration.ofMinutes(2), task.getIntervalDuration());
                    assertEquals(Duration.ofSeconds(30), task.getInitialDelayDuration());
                    verifyNoInteractions(service);
                });
    }

    @Test
    void disablesAutomaticChecksWhenConfigured() {
        contextRunner.withPropertyValues("app.sheets.check-enabled=false").run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(RegisterCollectionScheduler.class).isEmpty());
            assertTrue(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks().isEmpty());
            verifyNoInteractions(service);
        });
    }

    @Test
    void repeatsChecksEvenAfterReadFailure() throws Exception {
        var attempts = new AtomicInteger();
        var completed = new CountDownLatch(3);
        when(service.collect()).thenAnswer(invocation -> {
            int attempt = attempts.incrementAndGet();
            completed.countDown();
            if (attempt == 1) {
                throw new IOException("Falha de rede simulada");
            }
            return new SheetImportResult(List.of(), List.of(new SheetImportResult.RowError(2, "CPF inválido")), 0);
        });
        contextRunner.withPropertyValues("app.sheets.check-interval=30ms", "app.sheets.check-initial-delay=0s")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(completed.await(5, TimeUnit.SECONDS), "O agendamento deve continuar após a falha.");
                    assertTrue(attempts.get() >= 3);
                });
    }

    @Test
    void rejectsZeroIntervalAtStartup() {
        contextRunner.withPropertyValues("app.sheets.check-interval=0s")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
