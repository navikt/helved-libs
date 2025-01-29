package libs.tracing

import libs.utils.*
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.*
import io.opentelemetry.context.*
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter

private val log = logger("libs.tracing")

val tracer: Tracer = GlobalOpenTelemetry.getTracer("helved-tracer")

val currentSpan: Span get() = Span.current()
/** 
 * Start a new span and append it to the context
 */
fun startSpan(name: String, block: (Span) -> Unit) {
    val span: Span = tracer.spanBuilder(name).startSpan()
    try {
        val scope: Scope = span.makeCurrent()
        scope.use {
            block(span)
        }
    } catch (e: Exception) {
        span.setStatus(StatusCode.ERROR, "Error: ${e.message}")
        throw e
    } finally {
        span.end()
    }
}

/** 
 * When passing spans across execution boundaries, use context propagation
 */
fun propagateSpan(): Context {
    val span = Span.current()
    return Context.current().with(span)
}

fun getTraceparent(): String? {
    val ctx = currentSpan.spanContext
    if (!ctx.isValid) return null
    val sampled = if (ctx.traceFlags.isSampled) "01" else "00"
    return "00-${ctx.traceId}-${ctx.spanId}-$sampled"
}

fun propagateSpan(traceparent: String): Context {
    val split = traceparent.split("-")
    if (split.size < 4) {
        log.warn("Invalid traceparent: $traceparent")
        return Context.current()
    }
    val traceId = split[1]
    val parentSpanId = split[2]
    val traceFlags = TraceFlags.getSampled()
    val traceState = TraceState.getDefault()
    val spanCtx = SpanContext.createFromRemoteParent(traceId, parentSpanId, traceFlags, traceState)
    return Context.current().with(Span.wrap(spanCtx))
}

fun setup(url: String = env("OTEL_EXPORTER_OTLP_ENDPOINT")) {
    val exporter = OtlpGrpcSpanExporter.builder()
        .setEndpoint(url)
        .build()

    val provider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()

    OpenTelemetrySdk.builder()
        .setTracerProvider(provider)
        .buildAndRegisterGlobal()
}

