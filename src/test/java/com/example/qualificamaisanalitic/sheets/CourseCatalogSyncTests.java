package com.example.qualificamaisanalitic.sheets;

import com.entities.Course;
import com.repositories.CourseRepository;
import com.sheets.CourseCatalogSync;
import com.sheets.GoogleCourseCatalogWriter;
import com.sheets.config.SheetsProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseCatalogSyncTests {
    @Test void enabledCataloguePublishesDeterministicFullSnapshot() throws Exception {
        var repository = mock(CourseRepository.class);
        var writer = mock(GoogleCourseCatalogWriter.class);
        var properties = new SheetsProperties();
        properties.setCatalogEnabled(true);
        var later = new Course(); later.setId(9L); later.setName("Zebra");
        var first = new Course(); first.setId(2L); first.setName("Alpha");
        when(repository.findAll()).thenReturn(List.of(later, first));
        new CourseCatalogSync(repository, writer, properties).reconcile();
        var capture = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(writer).replace(capture.capture());
        assertEquals(List.of(new com.sheets.CourseCatalogEntry("Alpha", 2L),
                new com.sheets.CourseCatalogEntry("Zebra", 9L)), capture.getValue());
    }

    @Test void ambiguousNamesAreRejectedBeforeWriting() throws Exception {
        var repository = mock(CourseRepository.class); var writer = mock(GoogleCourseCatalogWriter.class);
        var properties = new SheetsProperties(); properties.setCatalogEnabled(true);
        var a = new Course(); a.setId(1L); a.setName(" Java ");
        var b = new Course(); b.setId(2L); b.setName("JAVA");
        when(repository.findAll()).thenReturn(List.of(a,b));
        assertDoesNotThrow(() -> new CourseCatalogSync(repository, writer, properties).reconcile());
        verifyNoInteractions(writer);
    }
    @Test void concurrentReconciliationsFetchSnapshotsInsideTheSerializationLock() throws Exception {
        var repository = mock(CourseRepository.class); var writer = mock(GoogleCourseCatalogWriter.class);
        var p = new SheetsProperties(); p.setCatalogEnabled(true);
        var old = new Course(); old.setId(1L); old.setName("Old");
        var current = new Course(); current.setId(1L); current.setName("New");
        when(repository.findAll()).thenReturn(List.of(old),List.of(current));
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var observed = new java.util.concurrent.CopyOnWriteArrayList<String>();
        doAnswer(call -> {
            var entries = (List<com.sheets.CourseCatalogEntry>) call.getArgument(0);
            if (observed.isEmpty()) { entered.countDown(); assertTrue(release.await(2,java.util.concurrent.TimeUnit.SECONDS)); }
            observed.add(entries.getFirst().name()); return null;
        }).when(writer).replace(anyList());
        var sync = new CourseCatalogSync(repository,writer,p);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(sync::reconcile);
            assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
            var second = pool.submit(sync::reconcile);
            assertThrows(java.util.concurrent.TimeoutException.class, () -> second.get(100,java.util.concurrent.TimeUnit.MILLISECONDS));
            verify(repository,times(1)).findAll();
            release.countDown(); first.get(2,java.util.concurrent.TimeUnit.SECONDS); second.get(2,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(List.of("Old","New"),observed);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void googleFailureDoesNotEscapeAndNextReconciliationRetries() throws Exception {
        var repository = mock(CourseRepository.class); var writer = mock(GoogleCourseCatalogWriter.class);
        var properties = new SheetsProperties(); properties.setCatalogEnabled(true);
        when(repository.findAll()).thenReturn(List.of());
        doThrow(new java.io.IOException("secret must not be logged")).doNothing().when(writer).replace(anyList());
        var sync = new CourseCatalogSync(repository,writer,properties);
        assertDoesNotThrow(sync::reconcile); assertDoesNotThrow(sync::reconcile);
        verify(writer,times(2)).replace(List.of());
    }
    @Test void disabledCatalogueDoesNotReadDatabaseOrGoogle() {
        var repository = mock(CourseRepository.class);
        var writer = mock(GoogleCourseCatalogWriter.class);
        var sync = new CourseCatalogSync(repository, writer, new SheetsProperties());
        sync.reconcile();
        verifyNoInteractions(repository, writer);
    }
}
