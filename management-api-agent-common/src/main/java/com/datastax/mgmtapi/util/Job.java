package com.datastax.mgmtapi.util;

import com.google.common.annotations.VisibleForTesting;

import java.util.UUID;

public class Job {
    public enum JobStatus {
        ERROR, COMPLETED, WAITING;
    }

    private String jobId;
    private String jobType;
    private JobStatus status;
    private long submitTime;
    private long finishedTime;
    private Throwable error;

    public Job(String jobType) {
        this.jobType = jobType;
        jobId = UUID.randomUUID().toString();
        submitTime = System.currentTimeMillis();
        status = JobStatus.WAITING;
    }

    @VisibleForTesting
    // This method is only for testing purposes
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }

    public String getJobType() {
        return jobType;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public long getSubmitTime() {
        return submitTime;
    }

    public long getFinishedTime() {
        return finishedTime;
    }

    public void setFinishedTime(long finishedTime) {
        this.finishedTime = finishedTime;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }
}
