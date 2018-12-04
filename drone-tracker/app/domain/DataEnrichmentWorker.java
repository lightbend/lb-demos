package domain;

import akka.NotUsed;
import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.stream.*;
import akka.stream.javadsl.*;
import akka.util.Timeout;
import com.typesafe.config.Config;
import domain.models.Coordinate;
import domain.models.DeviceDataPacket;
import domain.models.Measurement;
import geocode.GeoName;
import geocode.ReverseGeoCode;
import scala.compat.java8.FutureConverters;
import util.DemoDataUtil;
import util.StringPair;

import java.time.Duration;
import java.util.ArrayList;

import static domain.DataEnrichmentProtocol.*;
import static akka.pattern.Patterns.ask;
import static domain.models.PipelineCommon.*;

public class DataEnrichmentWorker extends PipelineEntity<DeviceDataPacket> {
    public static Props props(ActorRef summarizeByOrgRegion, ActorRef summarizeByCityRegion) {
        return Props.create(DataEnrichmentWorker.class, summarizeByCityRegion, summarizeByOrgRegion);
    }
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final int BROADCAST_PARALLELISM;
    private final Duration DATA_SUBMIT_TIMEOUT;
    private final Duration FLOW_RESTART_MIN;
    private final Duration FLOW_RESTART_MAX;
    private final double FLOW_RESTART_RANDOM_FACTOR;

    private ReverseGeoCode reverseGC;
    private ActorRef summarizeByCityRegion;
    private ActorRef summarizeByOrgRegion;
    private ArrayList<String> orgNames;

    @Override protected LoggingAdapter getLog() { return log; }
    @Override protected String getPipelineName() { return "data-enrichment"; }
    @Override protected Config getPipelineConfig() {
        return getContext().getSystem().settings().config().getConfig("drone-tracker.pipelines.enrichment");
    }
    @Override protected Class<DeviceDataPacket> getClassA() { return DeviceDataPacket.class; }
    @Override protected OverflowStrategy getOverflowStrategy() { return OverflowStrategy.dropTail(); }

    /** constructor */
    public DataEnrichmentWorker(ActorRef summarizeByOrgRegion, ActorRef summarizeByCityRegion) {
        super();

        this.summarizeByOrgRegion = summarizeByOrgRegion;
        this.summarizeByCityRegion = summarizeByCityRegion;

        Config config = getContext().getSystem().settings().config();
        BROADCAST_PARALLELISM = config.getInt("drone-tracker.pipelines.enrichment.broadcast-parallelism");
        DATA_SUBMIT_TIMEOUT = config.getDuration("drone-tracker.pipelines.enrichment.data-submit-timeout");
        FLOW_RESTART_MIN = config.getDuration("drone-tracker.pipelines.enrichment.restart.backoff-min");
        FLOW_RESTART_MAX = config.getDuration("drone-tracker.pipelines.enrichment.restart.backoff-max");
        FLOW_RESTART_RANDOM_FACTOR = config.getDouble("drone-tracker.pipelines.enrichment.restart.random-factor");

        this.orgNames = DemoDataUtil.generateOrgNames(config.getInt("drone-tracker.pipelines.enrichment.num-orgs"));
        this.reverseGC = ReverseGeoCode.loadDefaultRGC();
    }

    /**
     * lookup organization.  In a real application, this might be a data base call.  Since this
     * work is done within a cluster shard entity, caching the data in this actor would improve performance
     */
    private String lookupOrg(String id) {
        return this.orgNames.get(Math.abs(id.hashCode() % this.orgNames.size()));
    }

    /**
     * deterministically lookup organization.  In a real application, this might be another service call.  Since this
     * work is done within a cluster shard entity, caching the data in this actor would improve performance
     */
    private GeoName lookupCity(Coordinate coordinate) {
        return this.reverseGC.nearestPlace(coordinate.getLat(), coordinate.getLon());
    }

    /** define the actual enrich data pipeline flow */
    @Override
    protected Flow<DeviceDataPacket, Boolean, NotUsed> getPipelineFlow() {
        final String selfActorPath = getSelf().path().toString();
        return Flow.of(DeviceDataPacket.class)
                .mapConcat(d -> {
                    getMetricFactory().getCounter(
                            PIPELINE_THROUGHPUT_METRIC,
                            new StringPair(PIPELINE_LABEL, getPipelineName()),
                            new StringPair(STAGE_LABEL, "flatten-data"),
                            new StringPair(ACTOR_LABEL, selfActorPath)
                    ).increment();

                    ArrayList<EnrichedMeasurement> measurements = new ArrayList<>();
                    for (Measurement m: d.getMeasurements()) {
                        measurements.add(new EnrichedMeasurement(m).withId(d.getId()));
                    }
                    return measurements;
                }).named("flatten-data")
                .map(m -> m.withOrg(lookupOrg(m.getId()))).named("lookup-org")
                .map(m -> m.withGeoName(lookupCity(m.getLocation()))).named("lookup-geo")
                // wrap operation to talk to downstream actors with restart functionality so that actor timing out
                // (e.g. due to node restarts) does not permanently terminate this flow
                .via(RestartFlow.withBackoff(FLOW_RESTART_MIN, FLOW_RESTART_MAX, FLOW_RESTART_RANDOM_FACTOR, () ->
                        Flow.of(EnrichedMeasurement.class)
                                .mapAsync(BROADCAST_PARALLELISM, measurement -> {
                                    getMetricFactory().getCounter(
                                            PIPELINE_THROUGHPUT_METRIC,
                                            new StringPair(PIPELINE_LABEL, getPipelineName()),
                                            new StringPair(STAGE_LABEL, "broadcast-downstream"),
                                            new StringPair(ACTOR_LABEL, selfActorPath)
                                    ).increment();

                                    Timeout timeout = Timeout.create(DATA_SUBMIT_TIMEOUT);
                                    return FutureConverters.toJava(
                                            ask(summarizeByOrgRegion, measurement, timeout)
                                                    .zip(ask(summarizeByCityRegion, measurement, timeout)));
                                })
                )).named("broadcast-downstream")
                .map(x -> true)
                .watchTermination((x, done) -> getStreamUtil().handleFlowTermination(done))
                .log(getPipelineName());
    }
}
