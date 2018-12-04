package domain;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.Pair;
import akka.stream.javadsl.Flow;
import akka.util.Timeout;
import com.typesafe.config.Config;
import util.StreamUtil;
import util.StringPair;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static domain.DataEnrichmentProtocol.*;
import static domain.SummarizeCityTemperatureProtocol.*;
import static domain.models.PipelineCommon.*;

public class SummarizeCityTemperatureWorker extends PipelineEntity<EnrichedMeasurement> {
    public static Props props() { return Props.create(SummarizeCityTemperatureWorker.class); }

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final String AVG_CITY_TEMPERATURE_METRIC = "avg-city-temperature";
    private final String NUM_CITIES_METRIC = "num-cities";
    private final String CITY_LABEL = "city";
    private final String ORG_LABEL = "org";

    private final int MAX_ORGS_CITIES;
    private final Duration AGG_WINDOW;
    private final Duration WINDOW_SLIDE;
    private final Timeout DATA_SUBMIT_TIMEOUT;

    private HashMap<String, HashMap<String, EnrichedMeasurement>> cityTemperaturesByOrg = new HashMap<>();

    @Override protected LoggingAdapter getLog() { return log; }
    @Override protected String getPipelineName() { return "summarize-by-org"; }
    @Override protected Config getPipelineConfig() {
        return getContext().getSystem().settings().config().getConfig("drone-tracker.pipelines.summarize-by-org");
    }
    @Override protected Class<EnrichedMeasurement> getClassA() { return EnrichedMeasurement.class; }

    /** constructor */
    public SummarizeCityTemperatureWorker() {
        Config config = getContext().getSystem().settings().config();
        MAX_ORGS_CITIES = config.getInt("drone-tracker.pipelines.summarize-by-org.max-org-cities");
        AGG_WINDOW = config.getDuration("drone-tracker.pipelines.summarize-by-org.aggregation-window.window");
        WINDOW_SLIDE = config.getDuration("drone-tracker.pipelines.summarize-by-org.aggregation-window.slide");
        DATA_SUBMIT_TIMEOUT = Timeout.create(config.getDuration("drone-tracker.pipelines.summarize-by-org.data-submit-timeout"));
    }

    /** create summary pipeline */
    protected Flow<EnrichedMeasurement, Boolean, NotUsed> getPipelineFlow() {
        final ActorRef self = getSelf();
        final String selfActorPath = self.path().toString();
        return Flow.of(EnrichedMeasurement.class)
                .groupBy(MAX_ORGS_CITIES, m -> {
                    getMetricFactory().getCounter(
                            PIPELINE_THROUGHPUT_METRIC,
                            new StringPair(PIPELINE_LABEL, getPipelineName()),
                            new StringPair(STAGE_LABEL, "group-by-org-city"),
                            new StringPair(ACTOR_LABEL, selfActorPath)
                    ).increment();

                    return String.format("%s/%s", m.getOrg(), m.getGeoName().getQualifiedName());
                }, true).named("group-by-org-city")
                    .map(m -> (StreamUtil.WithTimestamp)m)
                    .statefulMapConcat(this.getStreamUtil().aggregate(AGG_WINDOW, WINDOW_SLIDE)).named("window-measurements")
                    .map(withTimestamps -> withTimestamps.stream().map(m -> (EnrichedMeasurement)m).collect(Collectors.toList()))
                    .map(measurements -> {
                        EnrichedMeasurement avgMeasurement = EnrichedMeasurement.averageByTemperature(measurements);
                        return new Pair<>("averaged", avgMeasurement);
                    }).named("aggregate-measurements")
                    .mergeSubstreams()
                .ask(self, Boolean.class, DATA_SUBMIT_TIMEOUT)
                .map(x -> {
                    return true;
                })
                .log(getPipelineName());
    }

    /** additional message handling */
    @Override
    protected Receive additionalMessageReceiver() {
        return receiveBuilder()
                .match(Pair.class, p -> p.first().equals("averaged"), p -> {
                    handleTempUpdate((EnrichedMeasurement) p.second());
                })
                .match(GetCitiesByOrg.class, m -> handleGetCitiesByOrg(m.org))
                .match(GetCityTemperatureByOrg.class, m -> handleGetCityTempByOrg(m.org, m.city))
                .build();
    }

    /* update internal city temperature */
    protected void handleTempUpdate(EnrichedMeasurement measurement) {
        HashMap<String, EnrichedMeasurement> orgTemps = this.cityTemperaturesByOrg.get(measurement.getOrg());
        if (orgTemps == null) {
            orgTemps = new HashMap<>();
            this.cityTemperaturesByOrg.put(measurement.getOrg(), orgTemps);
        }
        orgTemps.put(measurement.getGeoName().getQualifiedName(), measurement);
        getSender().tell(true, getSelf());

        this.getMetricFactory().getGaugeDouble(
                AVG_CITY_TEMPERATURE_METRIC,
                new StringPair(CITY_LABEL, measurement.getGeoName().getQualifiedName())
        ).set(measurement.getTemperature());
        this.getMetricFactory().getGaugeLong(
                NUM_CITIES_METRIC,
                new StringPair(ORG_LABEL, measurement.getOrg())
        ).set(orgTemps.size());
    }

    /** serve get city request */
    protected void handleGetCitiesByOrg(String org) {
        List<String> cities = new ArrayList<>();
        HashMap<String, EnrichedMeasurement> cityMeasurements = this.cityTemperaturesByOrg.get(org);
        if (cityMeasurements != null) {
            cities.addAll(cityMeasurements.keySet());
        }
        getSender().tell(new CitiesByOrg(org, cities), getSelf());
    }

    /** serve get city temperature request */
    protected void handleGetCityTempByOrg(String org, String city) {
        Double temp = null;
        HashMap<String, EnrichedMeasurement> cityMeasurements = this.cityTemperaturesByOrg.get(org);
        if (cityMeasurements != null) {
            EnrichedMeasurement measurement = cityMeasurements.get(city);
            if (measurement != null) {
                temp = measurement.getTemperature();
            }
        }
        getSender().tell(new CityTemperatureByOrg(org, city, temp), getSelf());
    }
}
