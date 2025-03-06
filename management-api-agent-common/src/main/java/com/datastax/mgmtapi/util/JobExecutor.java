/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.util;

import com.datastax.mgmtapi.ShimLoader;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import javax.management.NotificationFilter;
import org.apache.cassandra.utils.Pair;
import org.apache.cassandra.utils.progress.ProgressEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobExecutor {
  private static final Logger logger = LoggerFactory.getLogger(JobExecutor.class);

  ExecutorService executorService = Executors.newFixedThreadPool(1);
  Cache<String, Job> jobCache = CacheBuilder.newBuilder().recordStats().maximumSize(1000).build();

  public JobExecutor() {
    createRepairListener();
  }

  private void createRepairListener() {
    ShimLoader.instance
        .get()
        .getStorageService()
        .addNotificationListener(
            (notification, handback) -> {
              final int repairNo =
                  Integer.parseInt(((String) notification.getSource()).split(":")[1]);

              String jobId = String.format("repair-%d", repairNo);
              Job job = getJobWithId(jobId);
              if (job != null) {
                Map<String, Integer> data = (Map<String, Integer>) notification.getUserData();
                ProgressEventType progress = ProgressEventType.values()[data.get("type")];

                switch (progress) {
                  case START:
                    job.setStatusChange(progress, notification.getMessage());
                    job.setStartTime(System.currentTimeMillis());
                    break;
                  case NOTIFICATION:
                  case PROGRESS:
                    break;
                  case ERROR:
                  case ABORT:
                    job.setError(new RuntimeException(notification.getMessage()));
                    job.setStatusChange(progress, notification.getMessage());
                    job.setStatus(Job.JobStatus.ERROR);
                    job.setFinishedTime(System.currentTimeMillis());
                    break;
                  case SUCCESS:
                    job.setStatusChange(progress, notification.getMessage());
                    // SUCCESS / ERROR does not mean the job has completed yet (COMPLETE is that)
                    break;
                  case COMPLETE:
                    job.setStatusChange(progress, notification.getMessage());
                    job.setStatus(Job.JobStatus.COMPLETED);
                    job.setFinishedTime(System.currentTimeMillis());
                    break;
                }
                updateJob(job);
              }
            },
            (NotificationFilter)
                notification -> {
                  if (notification == null) {
                    return false;
                  }
                  logger.trace(
                      "Received notification: {}, {}",
                      notification.toString(),
                      notification.getSource());
                  if (notification.getType().equals("progress")) {
                    // Add error handling here as well as the getType filtering, as well as matching
                    // to some existing job
                    try {
                      String[] split = ((String) notification.getSource()).split(":");
                      if (split.length > 1) {
                        Integer.parseInt(split[1]);
                        return true;
                      }
                    } catch (NumberFormatException e) {
                      return false;
                    }
                  }
                  return false;
                },
            null);
  }

  public Pair<String, CompletableFuture<Void>> submit(String jobType, Runnable runnable) {
    // Where do I create the job details? Here? Add it to the Cache first?
    // Update the status on the callbacks and do nothing else?

    String jobId = UUID.randomUUID().toString();
    final Job job = createJob(jobType, jobId);

    CompletableFuture<Void> submittedJob =
        CompletableFuture.runAsync(runnable, executorService)
            .thenAccept(
                empty -> {
                  job.setStatus(Job.JobStatus.COMPLETED);
                  job.setFinishedTime(System.currentTimeMillis());
                  updateJob(job);
                })
            .exceptionally(
                t -> {
                  job.setStatus(Job.JobStatus.ERROR);
                  job.setError(t);
                  job.setFinishedTime(System.currentTimeMillis());
                  updateJob(job);
                  return null;
                });

    logger.debug("Created a new async job {} with jobId: {}", jobType, jobId);
    return Pair.create(job.getJobId(), submittedJob);
  }

  public Job createJob(String jobType, String jobId) {
    final Job job = new Job(jobType, jobId);
    jobCache.put(jobId, job);
    return job;
  }

  public void updateJob(Job job) {
    jobCache.put(job.getJobId(), job);
  }

  public Job getJobWithId(String jobId) {
    return jobCache.getIfPresent(jobId);
  }

  public int runningTasks() {
    return ((ThreadPoolExecutor) executorService).getActiveCount();
  }

  public int queuedTasks() {
    return ((ThreadPoolExecutor) executorService).getQueue().size();
  }
}
