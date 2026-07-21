/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.datastax.mgmtapi.shims.CassandraAPI;
import com.datastax.mgmtapi.util.Job;
import com.datastax.mgmtapi.util.JobExecutor;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.progress.ProgressEventType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

public class JobExecutorTest {

  @Mock private StorageService storageService;
  @Mock private CassandraAPI cassandraApi;

  private JobExecutor jobExecutor;
  private NotificationListener repairListener;
  private AutoCloseable mocks;

  @Before
  public void setUp() {
    mocks = MockitoAnnotations.openMocks(this);
    ShimLoader.instance = () -> cassandraApi;
    when(cassandraApi.getStorageService()).thenReturn(storageService);

    jobExecutor = new JobExecutor();

    ArgumentCaptor<NotificationListener> listenerCaptor =
        ArgumentCaptor.forClass(NotificationListener.class);
    ArgumentCaptor<NotificationFilter> filterCaptor =
        ArgumentCaptor.forClass(NotificationFilter.class);
    verify(storageService, atLeastOnce())
        .addNotificationListener(listenerCaptor.capture(), filterCaptor.capture(), isNull());
    repairListener = listenerCaptor.getValue();
  }

  @After
  public void tearDown() throws Exception {
    mocks.close();
  }

  @Test
  public void testRepairListenerRecordsSuccessfulLifecycleEvents() {
    Job job = jobExecutor.createJob("repair", "repair-7");

    repairListener.handleNotification(
        createRepairNotification(7, ProgressEventType.START, "started"), null);
    repairListener.handleNotification(
        createRepairNotification(7, ProgressEventType.SUCCESS, "succeeded"), null);
    repairListener.handleNotification(
        createRepairNotification(7, ProgressEventType.COMPLETE, "complete"), null);

    Job updatedJob = jobExecutor.getJobWithId("repair-7");
    assertEquals(job, updatedJob);
    assertEquals(Job.JobStatus.COMPLETED, updatedJob.getStatus());
    assertEquals(3, updatedJob.getStatusChanges().size());
    assertEquals(ProgressEventType.START, updatedJob.getStatusChanges().get(0).getStatus());
    assertEquals(ProgressEventType.SUCCESS, updatedJob.getStatusChanges().get(1).getStatus());
    assertEquals(ProgressEventType.COMPLETE, updatedJob.getStatusChanges().get(2).getStatus());
    assertTrue(updatedJob.getFinishedTime() > 0);
    assertNull(updatedJob.getError());
  }

  @Test
  public void testRepairListenerRecordsErrorAndAbortEvents() {
    jobExecutor.createJob("repair", "repair-8");
    jobExecutor.createJob("repair", "repair-9");

    repairListener.handleNotification(
        createRepairNotification(8, ProgressEventType.ERROR, "error"), null);
    repairListener.handleNotification(
        createRepairNotification(9, ProgressEventType.ABORT, "abort"), null);

    assertFailedJob("repair-8", ProgressEventType.ERROR, "error");
    assertFailedJob("repair-9", ProgressEventType.ABORT, "abort");
  }

  @Test
  public void testIgnoredRepairEventsAreNotRecorded() {
    Job job = jobExecutor.createJob("repair", "repair-10");

    repairListener.handleNotification(
        createRepairNotification(10, ProgressEventType.NOTIFICATION, "notification"), null);
    repairListener.handleNotification(
        createRepairNotification(10, ProgressEventType.PROGRESS, "progress"), null);

    assertTrue(job.getStatusChanges().isEmpty());
    assertEquals(Job.JobStatus.WAITING, job.getStatus());
  }

  @Test
  public void testStatusChangesAreReturnedAsDefensiveSnapshot() {
    Job job = jobExecutor.createJob("repair", "repair-11");
    repairListener.handleNotification(
        createRepairNotification(11, ProgressEventType.START, "started"), null);

    List<Job.StatusChange> snapshot = job.getStatusChanges();
    repairListener.handleNotification(
        createRepairNotification(11, ProgressEventType.SUCCESS, "succeeded"), null);

    assertEquals(1, snapshot.size());
    assertEquals(2, job.getStatusChanges().size());
  }

  @Test
  public void testGetJobStatusCanReadWhileListenerAppendsStatusChanges() throws Exception {
    NodeOpsProvider.service = jobExecutor;
    NodeOpsProvider nodeOpsProvider = new NodeOpsProvider();
    jobExecutor.createJob("repair", "repair-12");

    ExecutorService executorService = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch = new CountDownLatch(1);
    try {
      Future<?> writer =
          executorService.submit(
              () -> {
                await(startLatch);
                for (int i = 0; i < 250; i++) {
                  repairListener.handleNotification(
                      createRepairNotification(12, ProgressEventType.SUCCESS, "success-" + i),
                      null);
                }
                repairListener.handleNotification(
                    createRepairNotification(12, ProgressEventType.COMPLETE, "complete"), null);
              });

      Future<?> reader =
          executorService.submit(
              () -> {
                await(startLatch);
                for (int i = 0; i < 250; i++) {
                  Map<String, String> status = nodeOpsProvider.getJobStatus("repair-12");
                  assertFalse(status.isEmpty());
                  assertEquals("repair-12", status.get("id"));
                }
              });

      startLatch.countDown();
      writer.get(10, TimeUnit.SECONDS);
      reader.get(10, TimeUnit.SECONDS);
    } finally {
      executorService.shutdownNow();
    }

    assertEquals(Job.JobStatus.COMPLETED, jobExecutor.getJobWithId("repair-12").getStatus());
  }

  @Test
  public void testMissingRepairJobLifecycleNotificationIsSilentlyIgnored() {
    Logger logger = (Logger) LoggerFactory.getLogger(JobExecutor.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    try {
      repairListener.handleNotification(
          createRepairNotification(999, ProgressEventType.COMPLETE, "complete"), null);

      assertTrue(listAppender.list.isEmpty());
    } finally {
      logger.detachAppender(listAppender);
      listAppender.stop();
    }
  }

  @Test
  public void testMissingRepairJobProgressNotificationRemainsSilent() {
    Logger logger = (Logger) LoggerFactory.getLogger(JobExecutor.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    try {
      repairListener.handleNotification(
          createRepairNotification(999, ProgressEventType.PROGRESS, "progress"), null);

      assertTrue(listAppender.list.isEmpty());
    } finally {
      logger.detachAppender(listAppender);
      listAppender.stop();
    }
  }

  private void assertFailedJob(
      String jobId, ProgressEventType expectedEvent, String expectedMessage) {
    Job job = jobExecutor.getJobWithId(jobId);
    assertEquals(Job.JobStatus.ERROR, job.getStatus());
    assertEquals(1, job.getStatusChanges().size());
    assertEquals(expectedEvent, job.getStatusChanges().get(0).getStatus());
    assertEquals(expectedMessage, job.getError().getMessage());
    assertTrue(job.getFinishedTime() > 0);
  }

  private static void await(CountDownLatch startLatch) {
    try {
      if (!startLatch.await(5, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting to start concurrent job status test");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }

  private Notification createRepairNotification(
      int repairId, ProgressEventType progressEventType, String message) {
    Notification notification =
        new Notification("progress", "repair:" + repairId, System.nanoTime(), message);
    Map<String, Integer> userData = new HashMap<>();
    userData.put("type", progressEventType.ordinal());
    notification.setUserData(Collections.unmodifiableMap(userData));
    return notification;
  }
}
