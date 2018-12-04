package controllers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import domain.models.DeviceDataPacket;
import play.libs.Json;
import play.mvc.*;
import scala.compat.java8.FutureConverters;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import domain.DeviceDataProcessorProtocol.*;
import util.MetricFactory;

import static akka.pattern.Patterns.ask;

/**
 * this controller handles incoming device requests
 */

@Singleton
public class DeviceController extends Controller {
    private ActorRef deviceDataProcessor;
    private ActorSystem system;
    private final Duration REQUEST_TIMEOUT;

    private MetricFactory metricFactory;

    private final String DATA_PACKETS_RECEIVED_METRIC = "data-packets-received";

    /**
     * constructor
     */
    @Inject public DeviceController(
            Config config,
            ActorSystem system,
            @Named("device-data-processor") ActorRef deviceDataProcessor
    ) {
        this.deviceDataProcessor = deviceDataProcessor;
        this.metricFactory = new MetricFactory(system);
        this.REQUEST_TIMEOUT = config.getDuration("rest.request-timeout");
    }

    public Result getDevice(String deviceId) {
        return ok("hello device: " + deviceId);
    }

    /**
     * called by devices to upload data for processing
     * @return
     */
    @BodyParser.Of(BodyParser.Json.class)
    public CompletionStage<Result> sendDataPacket() {
        DeviceDataPacket packet = Json.fromJson(request().body().asJson(), DeviceDataPacket.class);

        this.metricFactory.getCounter(DATA_PACKETS_RECEIVED_METRIC).increment();
        return FutureConverters.toJava(
                ask(this.deviceDataProcessor, packet, REQUEST_TIMEOUT.toMillis()))
                    .thenApply(response -> created(Json.toJson((DataPacketResponse)response)));
    }
}
