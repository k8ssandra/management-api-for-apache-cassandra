/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.cassandra.utils.progress.ProgressEventType;

public class Job {
  public enum JobStatus {
    ERROR,
    COMPLETED,
    WAITING;
  }

  private String jobId;
  private String jobType;
  private volatile JobStatus status;
  private long submitTime;
  private volatile long startTime;
  private volatile long finishedTime;
  private volatile Throwable error;

  public static final class StatusChange {
    private final ProgressEventType status;
    private final long changeTime;

    private final String message;

    public StatusChange(ProgressEventType type, String message) {
      changeTime = System.currentTimeMillis();
      status = type;
      this.message = message;
    }

    public ProgressEventType getStatus() {
      return status;
    }

    public long getChangeTime() {
      return changeTime;
    }

    public String getMessage() {
      return message;
    }
  }

  private final CopyOnWriteArrayList<StatusChange> statusChanges;

  public Job(String jobType, String jobId) {
    this.jobType = jobType;
    this.jobId = jobId;
    submitTime = System.currentTimeMillis();
    status = JobStatus.WAITING;
    statusChanges = new CopyOnWriteArrayList<>();
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

  public void setStatusChange(ProgressEventType type, String message) {
    statusChanges.add(new StatusChange(type, message));
  }

  public List<StatusChange> getStatusChanges() {
    return new ArrayList<>(statusChanges);
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
