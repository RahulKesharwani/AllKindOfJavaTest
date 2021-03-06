package com.learn.opencensus.bigtable.it;

import com.google.api.gax.rpc.ServerStream;
import com.google.cloud.bigtable.data.v2.BigtableDataClient;
import com.google.cloud.bigtable.data.v2.BigtableDataSettings;
import com.google.cloud.bigtable.data.v2.models.Query;
import com.google.cloud.bigtable.data.v2.models.Row;
import io.opencensus.common.Scope;
import io.opencensus.exporter.trace.zipkin.ZipkinTraceExporter;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Status;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.config.TraceParams;
import io.opencensus.trace.samplers.Samplers;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class BasicReadRow {

  private static final Logger logger = Logger.getLogger(BasicReadRow.class.getName());

  public static void main(String[] args) throws IOException, InterruptedException {

    // 1. Configure exporter to export traces to Zipkin.
    BigtableDataSettings.enableOpenCensusStats();

    // 1. Configure exporter to export traces to Zipkin.
    ZipkinTraceExporter.createAndRegister(
        "http://localhost:9411/api/v2/spans", "Another-service-tracing");

    // 2. Configure 100% sample rate, otherwise, few traces will be sampled.
    TraceConfig traceConfig = Tracing.getTraceConfig();
    TraceParams activeTraceParams = traceConfig.getActiveTraceParams();
    traceConfig.updateActiveTraceParams(
        activeTraceParams.toBuilder().setSampler(Samplers.alwaysSample()).build());

    //    LoggingTraceExporter.register();
    BigtableDataSettings.Builder settingsB =
        BigtableDataSettings.newBuilder()
            .setProjectId("grass-clump-479")
            .setInstanceId("bigtableio-test");

    // 3. Get the global singleton Tracer object.
    Tracer tracer = Tracing.getTracer();

    BigtableDataClient dataClient = BigtableDataClient.create(settingsB.build());

    // 4. Create a scoped span, a scoped span will automatically end when closed.
    // It implements AutoClosable, so it'll be closed when the try block ends.
    try (Scope scope = tracer.spanBuilder("main").startScopedSpan()) {
      System.out.println("About to do some busy work...");
      doWork(10000, dataClient);
    }

    dataClient.close();
  }

  private static void doWork(int totalRows, BigtableDataClient client) throws InterruptedException {
    // 6. Get the global singleton Tracer object.
    Tracer tracer = Tracing.getTracer();

    try (Scope scope = tracer.spanBuilder("doBigtableThing").startScopedSpan()) {
      ServerStream<Row> row =
          client.readRows(Query.create("BeamCloudBigtableIOIntegrationTest").limit(totalRows));

      int i = 0;
      for (Row r : row) {
        System.out.println(r.getKey().toStringUtf8());
        i++;
        if (i % 100 == 0) {
          Thread.sleep(1000);
        }
      }
    }
  }

  public static void main3(String[] args) {
    // 1. Configure exporter to export traces to Zipkin.
    ZipkinTraceExporter.createAndRegister(
        "http://localhost:9411/api/v2/spans", "tracing-to-zipkin-service");

    // 2. Configure 100% sample rate, otherwise, few traces will be sampled.
    TraceConfig traceConfig = Tracing.getTraceConfig();
    TraceParams activeTraceParams = traceConfig.getActiveTraceParams();
    traceConfig.updateActiveTraceParams(
        activeTraceParams.toBuilder().setSampler(Samplers.alwaysSample()).build());

    // 3. Get the global singleton Tracer object.
    Tracer tracer = Tracing.getTracer();

    // 4. Create a scoped span, a scoped span will automatically end when closed.
    // It implements AutoClosable, so it'll be closed when the try block ends.
    try (Scope scope = tracer.spanBuilder("main").startScopedSpan()) {
      System.out.println("About to do some busy work...");
      for (int i = 0; i < 10; i++) {
        doWork(i);
      }
    }

    // 5. Gracefully shutdown the exporter, so that it'll flush queued traces to Zipkin.
    Tracing.getExportComponent().shutdown();
  }

  private static void doWork(int i) {
    Tracer tracer = Tracing.getTracer();

    // 7. Start another span. If another span was already started, it'll use that span as the parent
    // span.
    // In this example, the main method already started a span, so that'll be the parent span, and
    // this will be
    // a child span.
    try (Scope scope = tracer.spanBuilder("doWork").startScopedSpan()) {
      // Simulate some work.
      Span span = tracer.getCurrentSpan();

      try {
        System.out.println("doing busy work");
        Thread.sleep(100L);
      } catch (InterruptedException e) {
        // 6. Set status upon error
        span.setStatus(Status.INTERNAL.withDescription(e.toString()));
      }

      // 7. Annotate our span to capture metadata about our operation
      Map<String, AttributeValue> attributes = new HashMap<String, AttributeValue>();
      attributes.put("use", AttributeValue.stringAttributeValue("demo"));
      span.addAnnotation("Invoking doWork", attributes);
    }
  }
}
