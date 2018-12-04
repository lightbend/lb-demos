package util;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorRef;
import akka.event.LoggingAdapter;
import akka.japi.function.Creator;
import akka.japi.function.Function;
import akka.stream.QueueOfferResult;
import domain.DataStreamProtocol;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.CompletionStage;

import static domain.models.PipelineCommon.*;

public class StreamUtil {
    private LoggingAdapter log;
    private final String pipelineName;
    private MetricFactory metricFactory;
    private ActorRef parent;
    private String parentActorPath;
    private String DATA_SUBMIT_ERRORS = "data-submit-errors";
    private String ERROR_LABEL = "error";

    /** constructor */
    public StreamUtil(String pipelineName, ActorRef parent, MetricFactory metricFactory, LoggingAdapter log) {
        this.parent = parent;
        this.metricFactory = metricFactory;
        this.log = log;
        this.pipelineName = pipelineName;
        final String parentActorPath = parent.path().toString();
    }

    /** handler for Source.queue.offer() */
    public boolean handleOfferResponse(ActorRef sender, QueueOfferResult offerResult, Throwable ex) {
        sender.tell(new DataStreamProtocol.StreamAck(), this.parent);
        String errorValue = null;
        if (ex == null) {
            if (offerResult.equals(QueueOfferResult.enqueued())) {
              // no-op
            } if (offerResult.equals(QueueOfferResult.dropped())) {
                errorValue = "dropped";
            } else {
                errorValue = "result=" + offerResult;
            }
        } else {
            errorValue = "ex=" + ex;
        }
        if (errorValue != null) {
            this.metricFactory.getCounter(DATA_SUBMIT_ERRORS, new StringPair(ERROR_LABEL, errorValue));
            return false;
        } else {
            return true;
        }
    }

    /** handler for flow termination */
    public NotUsed handleFlowTermination(CompletionStage<Done> done) {
        done.exceptionally(ex -> {
            this.metricFactory.getCounter(
                    PIPELINE_TERMINATED,
                    new StringPair(PIPELINE_LABEL, this.pipelineName),
                    new StringPair(ACTOR_LABEL, this.parentActorPath),
                    new StringPair(REASON_LABEL, ex.getClass().getName())
            ).increment();
            log.error(ex, "unexpected termination in {} pipeline: {}", this.pipelineName, this.pipelineName, ex);
            return null;
        });
        return NotUsed.getInstance();
    }

    /** for use with windowing */
    public static interface WithTimestamp {
        long getTimestamp();
    }

    /** used to maintain state for time window aggreagation */
    public class AggregationState {
        public long windowStart = System.currentTimeMillis();
        public LinkedList<WithTimestamp> measurements = new LinkedList<>();
    }

    /** simple event windowing function */
    public Creator<Function<WithTimestamp, Iterable<LinkedList<WithTimestamp>>>> aggregate(Duration window, Duration slide) {
        return () -> {
            final AggregationState state = new AggregationState();

            return measurement ->  {
                ArrayList<LinkedList<WithTimestamp>> windowedMeasurements = new ArrayList<>();
                state.measurements.add(measurement);
                long delta = measurement.getTimestamp() - state.windowStart;
                if (delta > window.toMillis()) {
                    if (state.measurements.size() > 0) {
                        LinkedList<WithTimestamp> copy = new LinkedList<>();
                        copy.addAll(state.measurements);
                        windowedMeasurements.add(copy);
                        long curTimestamp = 0;
                        do {
                            curTimestamp = state.measurements.pop().getTimestamp();
                        } while (state.measurements.size() > 0 && curTimestamp < state.windowStart);
                    }

                    state.windowStart = state.windowStart + slide.toMillis();
                }
                return windowedMeasurements;
            };
        };
    }
}
