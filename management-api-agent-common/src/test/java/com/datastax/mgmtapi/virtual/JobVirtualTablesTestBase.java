/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.virtual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import com.datastax.mgmtapi.shims.CassandraAPI;
import com.datastax.mgmtapi.util.Job;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.UntypedResultSet;
import org.apache.cassandra.db.virtual.VirtualKeyspace;
import org.apache.cassandra.db.virtual.VirtualKeyspaceRegistry;
import org.apache.cassandra.db.virtual.VirtualTable;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.exceptions.TruncateException;
import org.apache.cassandra.utils.progress.ProgressEventType;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/** Runs the same virtual-table SELECTs against each supported Cassandra runtime. */
public abstract class JobVirtualTablesTestBase {
  private final List<Job> jobs = new ArrayList<>();

  protected abstract CassandraAPI createApi();

  @BeforeClass
  public static void initializeCassandra() {
    DatabaseDescriptor.clientInitialization();
  }

  @Before
  public void registerTables() {
    createApi().registerJobVirtualTables();
    assertThat(VirtualKeyspaceRegistry.instance.getKeyspaceNullable("management_api")).isNotNull();
    ManagementApiVirtualKeyspace.register(() -> jobs);
    QueryProcessor.clearInternalStatementsCache();
  }

  @Test
  public void emptyJobsAndUnknownIdsReturnNoRows() {
    assertThat(query("SELECT * FROM management_api.jobs").isEmpty()).isTrue();
    assertThat(query("SELECT * FROM management_api.job_events").isEmpty()).isTrue();
    assertThat(query("SELECT * FROM management_api.jobs WHERE job_id = 'missing'").isEmpty())
        .isTrue();
    assertThat(query("SELECT * FROM management_api.job_events WHERE job_id = 'missing'").isEmpty())
        .isTrue();
  }

  @Test
  public void jobsExposeWaitingCompletedAndFailedState() {
    Job waiting = addJob("repair", "waiting");
    Job completed = addJob("cleanup", "completed");
    completed.setStartTime(1000);
    completed.setFinishedTime(2000);
    completed.setStatus(Job.JobStatus.COMPLETED);
    Job failed = addJob("rebuild", "failed");
    failed.setError(new RuntimeException("rebuild failed"));
    failed.setFinishedTime(3000);
    failed.setStatus(Job.JobStatus.ERROR);

    assertThat(query("SELECT * FROM management_api.jobs").size()).isEqualTo(3);
    UntypedResultSet.Row row = job("waiting");
    assertThat(row.getString("job_type")).isEqualTo("repair");
    assertThat(row.getString("status")).isEqualTo("WAITING");
    assertThat(row.getTimestamp("submitted_at").getTime()).isEqualTo(waiting.getSubmitTime());
    assertThat(row.has("started_at")).isFalse();
    assertThat(row.has("finished_at")).isFalse();
    assertThat(row.has("error")).isFalse();

    row = job("completed");
    assertThat(row.getString("status")).isEqualTo("COMPLETED");
    assertThat(row.getTimestamp("started_at").getTime()).isEqualTo(1000);
    assertThat(row.getTimestamp("finished_at").getTime()).isEqualTo(2000);
    assertThat(row.has("error")).isFalse();

    row = job("failed");
    assertThat(row.getString("status")).isEqualTo("ERROR");
    assertThat(row.getString("error")).isEqualTo("rebuild failed");
  }

  @Test
  public void queriesReflectLiveChangesAndFilterByJobId() {
    Job first = addJob("repair", "repair-1");
    addJob("repair", "repair-2").setStatusChange(ProgressEventType.ERROR, "other job");
    assertThat(job("repair-1").getString("status")).isEqualTo("WAITING");
    first.setStatus(Job.JobStatus.COMPLETED);
    first.setFinishedTime(4000);
    first.setStatusChange(ProgressEventType.COMPLETE, "done");

    assertThat(job("repair-1").getString("status")).isEqualTo("COMPLETED");
    UntypedResultSet events =
        query("SELECT * FROM management_api.job_events WHERE job_id = 'repair-1'");
    assertThat(events.size()).isEqualTo(1);
    assertThat(events.one().getString("message")).isEqualTo("done");
    assertThat(events.one().getString("event_type")).isEqualTo("COMPLETE");

    jobs.clear();
    assertThat(query("SELECT * FROM management_api.jobs").isEmpty()).isTrue();
    assertThat(query("SELECT * FROM management_api.job_events").isEmpty()).isTrue();
  }

  @Test
  public void eventsInTheSameMillisecondAreAllPreservedInOrder() {
    Job repair = addJob("repair", "repair-3");
    for (int i = 0; i < 1000; i++) {
      repair.setStatusChange(ProgressEventType.SUCCESS, "event-" + i);
    }
    UntypedResultSet events =
        query("SELECT * FROM management_api.job_events WHERE job_id = 'repair-3'");
    assertThat(events.size()).isEqualTo(1000);
    long previous = Long.MIN_VALUE;
    int i = 0;
    for (UntypedResultSet.Row row : events) {
      assertThat(row.getString("message")).isEqualTo("event-" + i++);
      long time = row.getTimestamp("event_time").getTime();
      assertThat(time).isGreaterThan(previous);
      previous = time;
    }
    assertThat(query("SELECT * FROM management_api.job_events WHERE job_id = 'repair-3'").size())
        .isEqualTo(1000);
  }

  @Test
  public void tablesRejectMutationsAndTruncation() {
    for (VirtualTable table :
        VirtualKeyspaceRegistry.instance.getKeyspaceNullable("management_api").tables()) {
      assertThrows(InvalidRequestException.class, () -> table.apply(null));
      assertThrows(
          TruncateException.class, () -> query("TRUNCATE management_api." + table.metadata().name));
    }
  }

  @Test
  public void shimRegistersSeparateKeyspaceAndLeavesSystemViewsIntact() {
    VirtualKeyspace systemViews = new VirtualKeyspace("system_views", Collections.emptyList());
    VirtualKeyspaceRegistry.instance.register(systemViews);
    Object registered = VirtualKeyspaceRegistry.instance.getKeyspaceNullable("management_api");
    createApi().registerJobVirtualTables();
    assertThat(VirtualKeyspaceRegistry.instance.getKeyspaceNullable("system_views"))
        .isSameAs(systemViews);
    assertThat(VirtualKeyspaceRegistry.instance.getKeyspaceNullable("management_api"))
        .isSameAs(registered);
    assertThat(VirtualKeyspaceRegistry.instance.getKeyspaceNullable("management_api").tables())
        .extracting(table -> table.metadata().name)
        .containsExactlyInAnyOrder("jobs", "job_events");
  }

  private Job addJob(String type, String id) {
    Job job = new Job(type, id);
    jobs.add(job);
    return job;
  }

  private UntypedResultSet.Row job(String id) {
    UntypedResultSet result =
        query("SELECT * FROM management_api.jobs WHERE job_id = '" + id + "'");
    assertThat(result.size()).isEqualTo(1);
    assertThat(result.one().getString("job_id")).isEqualTo(id);
    return result.one();
  }

  private UntypedResultSet query(String cql) {
    return QueryProcessor.executeInternal(cql);
  }
}
