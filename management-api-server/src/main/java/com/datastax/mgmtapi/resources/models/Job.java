/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.resources.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;

public class Job implements Serializable {
  public enum JobStatus {
    ERROR,
    COMPLETED,
    WAITING;
  }

  @JsonProperty(value = "id")
  private String jobId;

  @JsonProperty(value = "type")
  private String jobType;

  @JsonProperty(value = "status")
  private JobStatus status;

  @JsonProperty(value = "submit_time")
  private long submitTime;

  @JsonProperty(value = "end_time")
  private long finishedTime;

  @JsonProperty(value = "error")
  private String error;

  public class StatusChange {
    @JsonProperty(value = "status")
    String status;

    @JsonProperty(value = "change_time")
    long changeTime;

    @JsonProperty(value = "message")
    String message;

    public StatusChange(String type, String message) {
      changeTime = System.currentTimeMillis();
      status = type;
      this.message = message;
    }

    public String getStatus() {
      return status;
    }

    public long getChangeTime() {
      return changeTime;
    }

    public String getMessage() {
      return message;
    }
  }

  @JsonProperty(value = "status_changes")
  private List<StatusChange> statusChanges;

  @JsonCreator
  public Job(
      @JsonProperty(value = "id") String jobId,
      @JsonProperty(value = "type") String jobType,
      @JsonProperty(value = "status") String status,
      @JsonProperty(value = "submit_time") long submitTime,
      @JsonProperty(value = "end_time") long finishedTime,
      @JsonProperty(value = "error") String error,
      @JsonProperty(value = "status_changes") List<StatusChange> changes) {
    this.jobId = jobId;
    this.jobType = jobType;
    this.status = JobStatus.valueOf(status);
    this.submitTime = submitTime;
    this.finishedTime = finishedTime;
    this.error = error;
    this.statusChanges = changes;
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

  public long getSubmitTime() {
    return submitTime;
  }

  public long getFinishedTime() {
    return finishedTime;
  }

  public List<StatusChange> getStatusChanges() {
    return statusChanges;
  }

  public String getError() {
    return error;
  }
}
