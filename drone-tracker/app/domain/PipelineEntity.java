package domain;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.event.LoggingAdapter;
import akka.stream.ActorMaterializer;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.*;
import com.lightbend.cinnamon.akka.stream.CinnamonAttributes;
import com.typesafe.config.Config;
import util.MetricFactory;
import util.StreamUtil;
import util.StringPair;

import java.time.Duration;

import static domain.models.PipelineCommon.*;

/** base class for entities that encapsulate processing pipelines */
public abstract class PipelineEntity<A> extends AbstractActor {
    /** actor logger */
    protected abstract LoggingAdapter getLog();
    /** pipeline name */
    protected abstract String getPipelineName();
    /** restart configuration */
    protected abstract Config getPipelineConfig();
    /** get flow pipeline */
    protected abstract Flow<A, Boolean, NotUsed> getPipelineFlow();
    /** get class class object for type A */
    protected abstract Class<A> getClassA();
    /** get overflow strategy */
    protected OverflowStrategy getOverflowStrategy() {
        return OverflowStrategy.backpressure();
    }
    /** additional optional message handler */
    protected Receive additionalMessageReceiver() { return receiveBuilder().build(); }

    private final int BUFFER_SIZE;
    private final Duration DATA_SUBMIT_TIMEOUT;

    private SourceQueueWithComplete<A> sourceQueue;
    private StreamUtil streamUtil;
    private MetricFactory metricFactory = new MetricFactory(getContext());

    /** get streamtutil */
    protected StreamUtil getStreamUtil() {
        return this.streamUtil;
    }

    /** metric factory */
    protected MetricFactory getMetricFactory() {
        return this.metricFactory;
    }

    /** constructor */
    public PipelineEntity() {
        Config pipelineConfig = getPipelineConfig();
        BUFFER_SIZE = pipelineConfig.getInt("buffer-size");
        DATA_SUBMIT_TIMEOUT = pipelineConfig.getDuration("data-submit-timeout");

        this.streamUtil = new StreamUtil(getPipelineName(), getSelf(), getMetricFactory(), getLog());
    }

    /** actor init */
    @Override
    public void preStart() {
        this.sourceQueue = createWrappedPipeline();
    }

    /** create wrapped pipeline */
    protected SourceQueueWithComplete<A> createWrappedPipeline() {
        return Source.<A>queue(BUFFER_SIZE, getOverflowStrategy())
                .via(getPipelineFlow())
                .to(Sink.ignore())
                .addAttributes(CinnamonAttributes.isInstrumented()
                        .withReportByName(getPipelineName())
                        .withPerFlow()
                        .attributes()
                )
                .run(ActorMaterializer.create(getContext()));
    }

    /** message receiver */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(getClassA(), msg -> handleData(msg))
                .build()
                    .orElse(additionalMessageReceiver())
                    .orElse(receiveBuilder().matchAny(msg -> {
                        getLog().warning("pipeine = {}", getPipelineName());
                        getLog().warning("received unknown message {}", msg);
                    }).build());
    }

    /** handles device data */
    public void handleData(A data) {
        getMetricFactory().getCounter(
                PIPELINE_THROUGHPUT_METRIC,
                new StringPair(PIPELINE_LABEL, getPipelineName()),
                new StringPair(STAGE_LABEL, "pre-pipeline"),
                new StringPair(ACTOR_LABEL, getSelf().path().toString())
        ).increment();

        final ActorRef sender = getSender();
        this.sourceQueue.offer(data)
                .handle((result, ex) -> this.streamUtil.handleOfferResponse(sender, result, ex));
    }
}
