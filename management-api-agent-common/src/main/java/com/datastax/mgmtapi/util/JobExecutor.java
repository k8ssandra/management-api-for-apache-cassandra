package com.datastax.mgmtapi.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.*;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class JobExecutor {
    ExecutorService executorService = Executors.newFixedThreadPool(1);
    Cache<String, Job> jobCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build();

    public String submit(String jobType, Runnable runnable) {
        // Where do I create the job details? Here? Add it to the Cache first?
        // Update the status on the callbacks and do nothing else?
        final Job job = new Job(jobType);
        jobCache.put(job.getJobId(), job);

        CompletableFuture.runAsync(runnable, executorService)
                .thenAccept(empty -> {
                    job.setStatus(Job.JobStatus.COMPLETED);
                    job.setFinishedTime(System.currentTimeMillis());
                    jobCache.put(job.getJobId(), job);
                })
                .exceptionally(t -> {
                    job.setStatus(Job.JobStatus.ERROR);
                    job.setError(t);
                    job.setFinishedTime(System.currentTimeMillis());
                    jobCache.put(job.getJobId(), job);
                    return null;
                });

        return job.getJobId();
    }

    public Job getJobWithId(String jobId) {
        return jobCache.getIfPresent(jobId);
    }
}
