package com.test.stackdriver.trace;

import com.google.cloud.ServiceOptions;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.bigtable.data.v2.models.RowMutation;
import io.opencensus.common.Scope;
import io.opencensus.contrib.grpc.metrics.RpcViews;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.Sampler;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;
import org.apache.commons.lang3.RandomStringUtils;

public class SimpleMutationRead {
  private static final Logger logger = Logger.getLogger(SimpleMutationRead.class.getName());

  // [START configChanges]
  private static final String PROJECT_ID = ServiceOptions.getDefaultProjectId();
  private static final String INSTANCE_ID = System.getenv("INSTANCE_ID");
  private static final Boolean UNKNOWN_KEY = Boolean.getBoolean("unknown.key");
  private static final Integer ROWS_COUNT = Integer.getInteger("rows.count", 3);
  // [END configChanges]

  // Refer to table metadata names by byte array in the HBase API
  private static final String TABLE_ID = "Hello-Bigtable";
  private static final String FAMILY_ID = "cf1";

  private static final Tracer tracer = Tracing.getTracer();

  public static void main(String[] args) throws IOException {
    configureOpenCensusExporters(Samplers.alwaysSample());

    //    BigtableDataSettings.Builder builder =
    //        BigtableDataSettings.newBuilder().setProjectId(PROJECT_ID).setInstanceId(INSTANCE_ID);
    //    Set<StatusCode.Code> codes =
    // builder.stubSettings().readRowsSettings().getRetryableCodes();
    //    Set<StatusCode.Code> newStatusCodes = Sets.newHashSet(codes);
    //    newStatusCodes.add(StatusCode.Code.NOT_FOUND);
    //
    //    RetrySettings settings =
    //        builder
    //            .stubSettings()
    //            .readRowsSettings()
    //            .getRetrySettings()
    //            .toBuilder()
    //            .setMaxAttempts(3)
    //            .build();
    //
    //    builder
    //        .stubSettings()
    //        .readRowsSettings()
    //        .setRetryableCodes(newStatusCodes)
    //        .setRetrySettings(settings);
    //    builder
    //        .stubSettings()
    //        .readRowSettings()
    //        .setRetryableCodes(newStatusCodes)
    //        .setRetrySettings(settings);

    try (BigtableDataClient client = BigtableDataClient.create(PROJECT_ID, INSTANCE_ID)) {
      String rowPrefix = UUID.randomUUID().toString();

      logger.info("Started writing to the TABLE");

      for (int i = 0; i < ROWS_COUNT; i++) {
        client.mutateRow(
            RowMutation.create(TABLE_ID, rowPrefix + "-" + i)
                .setCell(FAMILY_ID, "qualifier", 10_000L, "value-" + i));
      }

      for (int i = 0; i < ROWS_COUNT; i++) {
        String finalRowKeyPrefix = UNKNOWN_KEY ? RandomStringUtils.random(5) : rowPrefix;
        try (Scope readScope = tracer.spanBuilder("test.readRow.trace").startScopedSpan()) {
          Span span = tracer.spanBuilder("readRow.trace").startSpan();
          Row row = client.readRow(TABLE_ID, finalRowKeyPrefix + "-" + i);

          if (row != null) {
            span.addAnnotation("Row Key Found");
            logger.info("Row: " + row.getKey().toStringUtf8());
          } else {
            span.addAnnotation("Row Key Not Found");
            logger.info("Row is null");
          }
          span.end();
        }
      }

      BigtableVeneer.sleep(5100);
    }
  }

  // [START config_oc_stackdriver_export]
  private static void configureOpenCensusExporters(Sampler sampler) throws IOException {
    TraceConfig traceConfig = Tracing.getTraceConfig();

    // For demo purposes, lets always sample.
    traceConfig.updateActiveTraceParams(
        traceConfig.getActiveTraceParams().toBuilder().setSampler(sampler).build());

    // Create the Stackdriver trace exporter
    StackdriverTraceExporter.createAndRegister(
        StackdriverTraceConfiguration.builder().setProjectId(PROJECT_ID).build());

    // [Start Stackdriver Monitoring]
    StackdriverStatsExporter.createAndRegister();

    // [END config_oc_stackdriver_export]

    // -------------------------------------------------------------------------------------------
    // Register all the gRPC views
    // OC will automatically go and instrument gRPC. It's going to capture app level metrics
    // like latency, req/res bytes, count of req/res messages, started rpc etc.
    // -------------------------------------------------------------------------------------------
    RpcViews.registerAllGrpcViews();
    RpcViews.registerAllGrpcBasicViews();
    BigtableDataSettings.enableOpenCensusStats();
  }
}
