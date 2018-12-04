package domain;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Pair;
import akka.stream.javadsl.*;
import akka.util.Timeout;
import com.typesafe.config.Config;
import domain.models.DroneStats;
import geocode.GeoName;
import util.StreamUtil;
import util.StringPair;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static domain.DataEnrichmentProtocol.*;
import static domain.SummarizeDronesProtocol.*;
import static domain.models.PipelineCommon.*;

public class SummarizeDronesWorker extends PipelineEntity<EnrichedMeasurement> {
    public static Props props() { return Props.create(SummarizeDronesWorker.class); }

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final String CITY_DRONE_COUNT = "city-drone-count";
    private final String DRONE_COUNT = "drone-count";
    private final String CITY_LABEL = "city";
    private final String ORG_LABEL = "org";

    private final int MAX_CITIES;
    private final Duration AGG_WINDOW;
    private final Duration WINDOW_SLIDE;
    private final Timeout DATA_SUBMIT_TIMEOUT;

    private HashMap<String, DroneSummary> droneSummaries = new HashMap<>();

    @Override protected LoggingAdapter getLog() { return log; }
    @Override protected String getPipelineName() { return "summarize-by-city"; }
    @Override protected Config getPipelineConfig() {
        return getContext().getSystem().settings().config().getConfig("drone-tracker.pipelines.summarize-by-city");
    }
    @Override protected Class<EnrichedMeasurement> getClassA() { return EnrichedMeasurement.class; }

    /** constructor */
    public SummarizeDronesWorker() {
        Config config = getContext().getSystem().settings().config();
        MAX_CITIES = config.getInt("drone-tracker.pipelines.summarize-by-city.max-cities");
        AGG_WINDOW = config.getDuration("drone-tracker.pipelines.summarize-by-city.aggregation-window.window");
        WINDOW_SLIDE = config.getDuration("drone-tracker.pipelines.summarize-by-city.aggregation-window.slide");
        DATA_SUBMIT_TIMEOUT = Timeout.create(config.getDuration("drone-tracker.pipelines.summarize-by-city.data-submit-timeout"));
    }

    /** define pipeline flow */
    protected Flow<EnrichedMeasurement, Boolean, NotUsed> getPipelineFlow() {
        final ActorRef self = getSelf();
        final String selfActorPath = self.path().toString();
        return Flow.of(EnrichedMeasurement.class)
                .groupBy(MAX_CITIES, m -> {
                    getMetricFactory().getCounter(
                            PIPELINE_THROUGHPUT_METRIC,
                            new StringPair(PIPELINE_LABEL, getPipelineName()),
                            new StringPair(STAGE_LABEL, "group-by-org"),
                            new StringPair(ACTOR_LABEL, selfActorPath)
                    ).increment();

                    return m.getOrg();
                }, true).named("group-by-org")
                    .map(m -> (StreamUtil.WithTimestamp)m)
                    .statefulMapConcat(this.getStreamUtil().aggregate(AGG_WINDOW, WINDOW_SLIDE)).named("window-measurements")
                    .map(withTimestamps -> withTimestamps.stream().map(m -> (EnrichedMeasurement)m).collect(Collectors.toList()))
                    .map(measurements -> {
                        Set<String> droneIds = new HashSet<>();
                        Map<String, Set<String>> dronesPerCity = new HashMap<>();
                        String org = "";
                        for (EnrichedMeasurement measurement: measurements) {
                            org = measurement.getOrg();
                            droneIds.add(measurement.getId());
                            String cityName = measurement.getGeoName().getQualifiedName();
                            Set<String> droneSet = dronesPerCity.get(cityName);
                            if (droneSet == null) {
                                droneSet = new HashSet<>();
                                dronesPerCity.put(cityName, droneSet);
                            }
                            droneSet.add(measurement.getId());
                        }

                        return new DroneSummary(org, droneIds, dronesPerCity);
                    }).named("aggregate-measurements")
                    .mergeSubstreams()
                .ask(self, Boolean.class, DATA_SUBMIT_TIMEOUT)
                .log(getPipelineName());
    }

    /** additional message handling */
    @Override
    protected Receive additionalMessageReceiver() {
        return receiveBuilder()
                .match(DroneSummary.class, msg -> handleUpdateDroneSummary(msg))
                .match(GetDroneSummary.class, msg -> handleGetDroneSummary(msg.org))
                .build();
    }

    /** update internal city temperature */
    protected void handleUpdateDroneSummary(DroneSummary droneSummary) {
        this.droneSummaries.put(droneSummary.org, droneSummary);
        getSender().tell(true, getSelf());

        for (String city: droneSummary.dronesPerCity.keySet()) {
            this.getMetricFactory().getGaugeLong(
                    CITY_DRONE_COUNT,
                    new StringPair(CITY_LABEL, city),
                    new StringPair(ORG_LABEL, droneSummary.org)
            ).set(droneSummary.dronesPerCity.get(city).size());
        }
        this.getMetricFactory().getGaugeLong(
                DRONE_COUNT,
                new StringPair(ORG_LABEL, droneSummary.org)
        ).set(droneSummary.droneIds.size());
    }

    /** serve get city temperature request */
    protected void handleGetDroneSummary(String org) {
        DroneSummary summary = this.droneSummaries.get(org);
        if (summary == null) {
            summary = new DroneSummary(org, new HashSet<>(), new HashMap<>());
        }

        getSender().tell(summary, getSelf());
    }
}
