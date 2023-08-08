/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.util;

import com.google.common.annotations.VisibleForTesting;
import org.apache.cassandra.utils.progress.ProgressEvent;
import org.apache.cassandra.utils.progress.ProgressEventType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Job {
  public enum JobStatus {
    ERROR,
    COMPLETED,
    WAITING;
  }

  private String jobId;
  private String jobType;
  private JobStatus status;
  private long submitTime;
  private long startTime;
  private long finishedTime;
  private Throwable error;

  class StatusChange {
    ProgressEventType status;
    long changeTime;

    public StatusChange(ProgressEventType type) {
      changeTime = System.currentTimeMillis();
      status = type;
    }
  }

  private List<StatusChange> statusChanges;

  public Job(String jobType, String jobId) {
    this.jobType = jobType;
    this.jobId = jobId;
    submitTime = System.currentTimeMillis();
    status = JobStatus.WAITING;
    statusChanges = new ArrayList<>();
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

  public void setStatusChange(ProgressEventType type) {
    statusChanges.add(new StatusChange(type));
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

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }
}
