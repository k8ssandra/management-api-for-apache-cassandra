/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.mgmtapi.virtual;

import com.datastax.mgmtapi.NodeOpsProvider;
import com.datastax.mgmtapi.util.Job;
import com.google.common.annotations.VisibleForTesting;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;
import org.apache.cassandra.db.marshal.TimestampType;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.db.virtual.AbstractVirtualTable;
import org.apache.cassandra.db.virtual.SimpleDataSet;
import org.apache.cassandra.db.virtual.VirtualKeyspace;
import org.apache.cassandra.db.virtual.VirtualKeyspaceRegistry;
import org.apache.cassandra.dht.LocalPartitioner;
import org.apache.cassandra.schema.TableMetadata;

/** Node-local, ephemeral job state, registered only by the Cassandra 5+ shims. */
public final class ManagementApiVirtualKeyspace {
  private static final String KEYSPACE = "management_api";

  private ManagementApiVirtualKeyspace() {}

  public static synchronized void register() {
    if (VirtualKeyspaceRegistry.instance.getKeyspaceNullable(KEYSPACE) == null) {
      register(() -> NodeOpsProvider.service.snapshotJobs());
    }
  }

  @VisibleForTesting
  static void register(Supplier<List<Job>> jobs) {
    VirtualKeyspaceRegistry.instance.register(
        new VirtualKeyspace(
            KEYSPACE, Arrays.asList(new JobsTable(jobs), new JobEventsTable(jobs))));
  }

  private static final class JobsTable extends AbstractVirtualTable {
    private final Supplier<List<Job>> jobs;

    private JobsTable(Supplier<List<Job>> jobs) {
      super(
          TableMetadata.builder(KEYSPACE, "jobs")
              .kind(TableMetadata.Kind.VIRTUAL)
              .comment("Local mgmt-api jobs, cleared on node restart or LRU")
              .partitioner(new LocalPartitioner(UTF8Type.instance))
              .addPartitionKeyColumn("job_id", UTF8Type.instance)
              .addRegularColumn("job_type", UTF8Type.instance)
              .addRegularColumn("status", UTF8Type.instance)
              .addRegularColumn("submitted_at", TimestampType.instance)
              .addRegularColumn("started_at", TimestampType.instance)
              .addRegularColumn("finished_at", TimestampType.instance)
              .addRegularColumn("error", UTF8Type.instance)
              .build());
      this.jobs = jobs;
    }

    @Override
    public DataSet data() {
      SimpleDataSet result = new SimpleDataSet(metadata());
      for (Job job : jobs.get()) {
        result
            .row(job.getJobId())
            .column("job_type", job.getJobType())
            .column("status", job.getStatus().name())
            .column("submitted_at", new Date(job.getSubmitTime()));
        long started = job.getStartTime();
        long finished = job.getFinishedTime();
        Throwable error = job.getError();
        if (started != 0) result.column("started_at", new Date(started));
        if (finished != 0) result.column("finished_at", new Date(finished));
        if (error != null) result.column("error", error.getLocalizedMessage());
      }
      return result;
    }
  }

  private static final class JobEventsTable extends AbstractVirtualTable {
    private final Supplier<List<Job>> jobs;

    private JobEventsTable(Supplier<List<Job>> jobs) {
      super(
          TableMetadata.builder(KEYSPACE, "job_events")
              .kind(TableMetadata.Kind.VIRTUAL)
              .comment("Status history of jobs currently in the nodes mgmt-api")
              .partitioner(new LocalPartitioner(UTF8Type.instance))
              .addPartitionKeyColumn("job_id", UTF8Type.instance)
              .addClusteringColumn("event_time", TimestampType.instance)
              .addRegularColumn("event_type", UTF8Type.instance)
              .addRegularColumn("message", UTF8Type.instance)
              .build());
      this.jobs = jobs;
    }

    @Override
    public DataSet data() {
      SimpleDataSet result = new SimpleDataSet(metadata());
      for (Job job : jobs.get()) {
        long previousTime = Long.MIN_VALUE;
        for (Job.StatusChange change : job.getStatusChanges()) {
          long eventTime = Math.max(change.getChangeTime(), previousTime + 1);
          result
              .row(job.getJobId(), new Date(eventTime))
              .column("event_type", change.getStatus().name())
              .column("message", change.getMessage());
          previousTime = eventTime;
        }
      }
      return result;
    }
  }
}
