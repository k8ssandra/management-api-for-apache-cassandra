package com.datastax.mgmtapi.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.*;

import javax.annotation.Nullable;
import java.util.concurrent.Executors;

public class JobExecutor {
    ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
    Cache<String, Job> jobCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build();

    public String submit(String jobType, Runnable runnable) {
        // Where do I create the job details? Here? Add it to the Cache first?
        // Update the status on the callbacks and do nothing else?
        final Job job = new Job(jobType);
        jobCache.put(job.getJobId(), job);

        ListenableFuture<?> submit = service.submit(runnable);
        Futures.addCallback(submit, new FutureCallback<Object>() {
            @Override
            public void onSuccess(@Nullable Object nothing) {
                job.setStatus(Job.JobStatus.COMPLETED);
                job.setFinishedTime(System.currentTimeMillis());
                jobCache.put(job.getJobId(), job);
            }

            @Override
            public void onFailure(Throwable throwable) {
                job.setStatus(Job.JobStatus.ERROR);
                job.setError(throwable);
                job.setFinishedTime(System.currentTimeMillis());
                jobCache.put(job.getJobId(), job);
            }
        });

        return job.getJobId();
    }

    public Job getJobWithId(String jobId) {
        return jobCache.getIfPresent(jobId);
    }
}
