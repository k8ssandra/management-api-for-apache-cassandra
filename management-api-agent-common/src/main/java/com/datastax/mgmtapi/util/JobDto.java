/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import org.apache.cassandra.utils.progress.ProgressEventType;

public class JobDto {

  public String jobId;
  public String jobType;
  public String status;
  public long submitTime;
  public long startTime;
  public long finishedTime;
  public Throwable error;

  public class StatusChange {
    public ProgressEventType status;
    public long changeTime;

    public String message;

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

  public List<StatusChange> statusChanges;

  public JobDto(String jobType, String jobId) {
    this.jobType = jobType;
    this.jobId = jobId;
    submitTime = System.currentTimeMillis();
    status = "WAITING";
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

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public void setStatusChange(ProgressEventType type, String message) {
    statusChanges.add(new StatusChange(type, message));
  }

  public List<StatusChange> getStatusChanges() {
    return statusChanges;
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
