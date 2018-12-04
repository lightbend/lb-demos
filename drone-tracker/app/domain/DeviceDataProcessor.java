package domain;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;
import domain.models.DeviceDataPacket;
import domain.DeviceDataProcessorProtocol.*;
import scala.compat.java8.FutureConverters;

import javax.inject.Inject;
import java.time.Duration;

import static akka.pattern.Patterns.ask;
import static domain.SummarizeCityTemperatureProtocol.*;
import static domain.SummarizeDronesProtocol.*;

public class DeviceDataProcessor extends AbstractActor {
    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private long sampleInterval;
    private long pushInterval;
    private Cluster cluster;

    private final boolean ENABLED;
    private final int DATA_ENRICHMENT_PARALLELISM;
    private final int SUMMARIZE_BY_ORG_PARALLELISM;
    private final int SUMMARIZE_BY_CITY_PARALLELISM;
    private final Duration DATA_SUBMIT_TIMEOUT;
    private final Duration REQUEST_TIMEOUT;

    private ActorRef dataEnrichmentRegion;
    private ActorRef summarizeCityTemperatureRegion;
    private ActorRef summarizeDronesRegion;

    /** constructor */
    @Inject
    public DeviceDataProcessor() {
        this.cluster = Cluster.get(context().system());

        Config config = context().system().settings().config();
        this.sampleInterval = config.getLong("domain.device-data-processor.initial-sample-interval-ms");
        this.pushInterval = config.getLong("domain.device-data-processor.initial-push-interval-ms");

        ENABLED = config.getBoolean("domain.device-data-processor.enabled");
        DATA_ENRICHMENT_PARALLELISM = config.getInt("drone-tracker.pipelines.enrichment.parallelism");
        SUMMARIZE_BY_ORG_PARALLELISM = config.getInt("drone-tracker.pipelines.summarize-by-org.parallelism");
        SUMMARIZE_BY_CITY_PARALLELISM = config.getInt("drone-tracker.pipelines.summarize-by-city.parallelism");
        DATA_SUBMIT_TIMEOUT = config.getDuration("drone-tracker.pipelines.enrichment.data-submit-timeout");
        REQUEST_TIMEOUT = config.getDuration("rest.request-timeout");
    }

    /** actor init */
    @Override
    public void preStart() {
        ActorSystem system = getContext().getSystem();
        ClusterShardingSettings shardSettings = ClusterShardingSettings.create(system);
        this.summarizeCityTemperatureRegion = ClusterSharding.get(system).start(
                SummarizeCityTemperatureWorker.class.getName(),
                SummarizeCityTemperatureWorker.props(),
                shardSettings,
                new SummarizeCityTemperatureProtocol.SummarizeCityTemperatureExtractor(SUMMARIZE_BY_ORG_PARALLELISM)
        );
        this.summarizeDronesRegion = ClusterSharding.get(system).start(
                SummarizeDronesWorker.class.getName(),
                SummarizeDronesWorker.props(),
                shardSettings,
                new SummarizeDronesProtocol.SummarizeDronesExtractor(SUMMARIZE_BY_CITY_PARALLELISM)
        );
        this.dataEnrichmentRegion = ClusterSharding.get(system).start(
                DataEnrichmentWorker.class.getName(),
                DataEnrichmentWorker.props(this.summarizeCityTemperatureRegion, this.summarizeDronesRegion),
                shardSettings,
                new DataEnrichmentProtocol.DataEnrichmentMessageExtractor(DATA_ENRICHMENT_PARALLELISM)
        );
    }

    /** message receiver */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(DeviceDataPacket.class, msg -> handleDeviceData(msg))
                .match(DataStreamProtocol.StreamAck.class, msg -> {})
                .match(GetCitiesByOrg.class, msg -> handleGetCities(msg))
                .match(GetAggregatedTemperature.class, msg -> handleGetTemperature(msg))
                .match(GetDroneSummary.class, msg -> handleGetDroneSummary(msg))
                .matchAny(msg -> log.warning("received unknown message {}", msg))
                .build();
    }

    /**
     * handler for device data
     * @param data
     */
    public void handleDeviceData(DeviceDataPacket data) {
        this.dataEnrichmentRegion.tell(data, getSelf());
        sender().tell(new DataPacketResponse(this.sampleInterval, this.pushInterval), self());
    }

    /** handler for getting drone summary */
    public void handleGetDroneSummary(GetDroneSummary msg) {
        this.summarizeDronesRegion.tell(msg, getSender());
    }

    /** handler for get cities query */
    public void handleGetCities(GetCitiesByOrg msg) {
        this.summarizeCityTemperatureRegion.tell(msg, getSender());
    }

    /** handler for get city temperature query */
    public void handleGetTemperature(GetAggregatedTemperature msg) {
        final ActorRef sender = getSender();
        FutureConverters.toJava(ask(
                this.summarizeCityTemperatureRegion,
                new GetCityTemperatureByOrg(msg.org, msg.city),
                REQUEST_TIMEOUT.toMillis()
        )).thenAccept(response -> {
            if (response instanceof CityTemperatureByOrg) {
                sender.tell(new AggregateCityTemperature(
                        msg.org,
                        msg.city,
                        ((CityTemperatureByOrg)response).temperature
                ), getSelf());
            } else {
                String errorMsg = String.format("unexpected return type on get city temp: got %s, expected %s",
                        response.getClass(), CityTemperatureByOrg.class);
                log.error(errorMsg);
                sender.tell(new UnexpectedError(errorMsg), getSelf());
            }
        }).exceptionally(ex -> {
            String errorMsg = String.format("unexpected return types on get city temp: %s", ex);
            log.error(ex, errorMsg);
            sender.tell(new UnexpectedError(errorMsg, ex), getSelf());
            return null;
        });
    }
}
