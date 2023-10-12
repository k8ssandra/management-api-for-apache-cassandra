/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.cassandra.utils.Pair;

public class JobExecutor {
  ExecutorService executorService = Executors.newFixedThreadPool(1);
  Cache<String, Job> jobCache = CacheBuilder.newBuilder().recordStats().maximumSize(1000).build();

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
