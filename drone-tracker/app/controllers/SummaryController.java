package controllers;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import domain.SummarizeDronesProtocol;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import scala.compat.java8.FutureConverters;
import util.MetricFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static akka.pattern.Patterns.ask;
import static domain.DeviceDataProcessorProtocol.*;
import static domain.SummarizeCityTemperatureProtocol.*;

public class SummaryController extends Controller {
    private ActorRef deviceDataProcessor;
    private ActorSystem system;
    private final Duration REQUEST_TIMEOUT;

    private MetricFactory metricFactory;
    /**
     * constructor
     */
    @Inject
    public SummaryController(
            Config config,
            ActorSystem system,
            @Named("device-data-processor") ActorRef deviceDataProcessor
    ) {
        this.deviceDataProcessor = deviceDataProcessor;
        this.metricFactory = new MetricFactory(system);
        this.REQUEST_TIMEOUT = config.getDuration("rest.request-timeout");
    }

    /** query for cities */
    public CompletionStage<Result> getCities(String org) {
        return FutureConverters.toJava(
                ask(this.deviceDataProcessor, new GetCitiesByOrg(org), REQUEST_TIMEOUT.toMillis()))
                    .thenApply(response -> ok(Json.toJson((CitiesByOrg)response)));
    }

    /** query for temperature */
    public CompletionStage<Result> getTemperature(String org, String city) {
        return FutureConverters.toJava(
                ask(this.deviceDataProcessor, new GetAggregatedTemperature(org, city), REQUEST_TIMEOUT.toMillis()))
                    .thenApply(response -> {
                        if (response instanceof AggregateCityTemperature) {
                            return ok(Json.toJson((AggregateCityTemperature)response));
                        } else {
                            throw new RuntimeException(String.format("unexpected response to get city temp: %s", response));
                        }
                    });
    }

    /** query for drone summary */
    public CompletionStage<Result> getDroneSummary(String org) {
        return FutureConverters.toJava(
                ask(this.deviceDataProcessor, new SummarizeDronesProtocol.GetDroneSummary(org), REQUEST_TIMEOUT.toMillis()))
                    .thenApply(response -> {
                        if (response instanceof SummarizeDronesProtocol.DroneSummary) {
                            return ok(Json.toJson((SummarizeDronesProtocol.DroneSummary)response));
                        } else {
                            throw new RuntimeException(String.format("unexpected response to get drone summary: %s", response));
                        }
                    });
    }
}
