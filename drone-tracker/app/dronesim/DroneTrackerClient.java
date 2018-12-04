package dronesim;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.*;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.japi.Pair;
import akka.stream.ActorMaterializer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import domain.models.DeviceDataPacket;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.util.concurrent.CompletionStage;

import static domain.SummarizeCityTemperatureProtocol.*;
import static domain.SummarizeDronesProtocol.*;
import static domain.DeviceDataProcessorProtocol.*;

public class DroneTrackerClient {
    private Log log = LogFactory.getLog(this.getClass());

    private Http httpClient;
    private String trackerEndpoint = null;
    private ActorMaterializer materializer;
    private long timeout;
    private ObjectMapper mapper = new ObjectMapper();

    /**
     * constructor
     * @param system
     */
    public DroneTrackerClient(String endpoint, Long timeout, ActorSystem system) {
        this.trackerEndpoint = endpoint;
        this.httpClient = Http.get(system);
        this.materializer = ActorMaterializer.create(system);
        this.timeout = timeout;
        this.mapper.registerModule(new Jdk8Module() );
    }

    /** url encoder */
    protected String uri(String path, Pair... params) {
        return Uri.create(String.format("%s/%s", this.trackerEndpoint, path))
                .query(Query.create(params))
                .toString();
    }

    /**
     * @param deviceId
     * @return
     */
    public CompletionStage<String> getDevice(String deviceId) {
        String url = uri(String.format("devices/%s", deviceId));
        return this.httpClient.singleRequest(HttpRequest.GET(url))
            .thenCompose(result -> result.entity().toStrict(this.timeout, this.materializer))
            .thenApply(entity -> entity.getData().utf8String());
    }

    /**
     * send drone data to server
     * @param data
     * @return
     */
    public CompletionStage<HttpResponse> sendData(DeviceDataPacket data) {
        String url = uri("devices/datapacket");
        ObjectMapper mapper = new ObjectMapper();
        byte[] dataAsBytes;
        try {
            dataAsBytes = mapper.writeValueAsString(data).getBytes();
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("error marshalling data: " + ex, ex);
        }

        return this.httpClient.singleRequest(HttpRequest.POST(url).withEntity(ContentTypes.APPLICATION_JSON, dataAsBytes));
    }

    /**
     * retreives list of cities for which there is temperature information for a given organization
     * @param org
     * @return
     */
    public CompletionStage<CitiesByOrg> getCities(String org) {
        String url = uri("summary/cities", Pair.create("org", org));
        Unmarshaller<HttpEntity, CitiesByOrg> unmarshaller = Jackson.unmarshaller(this.mapper, CitiesByOrg.class);
        return this.httpClient.singleRequest(HttpRequest.GET(url))
                .thenCompose(result -> {
                    if (result.status().equals(StatusCodes.OK)) {
                        return unmarshaller.unmarshal(result.entity(), this.materializer);
                    } else {
                        throw new RuntimeException(String.format("unexpected response from server on getCities(): code=%s, reason=%s", result.status().intValue(), result.status().reason()));
                    }
                });
    }

    /**
     * aggregated city temperature for given org
     * @param org
     * @param city
     * @return
     */
    public CompletionStage<AggregateCityTemperature> getTemperature(String org, String city) {
        String url = uri("summary/temperature", Pair.create("org", org), Pair.create("city", city));
        Unmarshaller<HttpEntity, AggregateCityTemperature> unmarshaller = Jackson.unmarshaller(this.mapper, AggregateCityTemperature.class);
        return this.httpClient.singleRequest(HttpRequest.GET(url))
                .thenCompose(result -> {
                    if (result.status().equals(StatusCodes.OK)) {
                        return unmarshaller.unmarshal(result.entity(), this.materializer);
                    } else {
                     throw new RuntimeException(String.format("unexpected response from server on getTemperature(): code=%s, reason=%s", result.status().intValue(), result.status().reason()));
                    }
                });
    }

    /**
     * get drone summary information
     * @param org
     * @return
     */
    public CompletionStage<DroneSummary> getDroneSummary(String org) {
        String url = uri("summary/drones", Pair.create("org", org));
        Unmarshaller<HttpEntity, DroneSummary> unmarshaller = Jackson.unmarshaller(this.mapper, DroneSummary.class);
        return this.httpClient.singleRequest(HttpRequest.GET(url))
                .thenCompose(result -> {
                    if (result.status().equals(StatusCodes.OK)) {
                        return unmarshaller.unmarshal(result.entity(), this.materializer);
                    } else {
                        throw new RuntimeException(String.format("unexpected response from server on getDroneSummary(): code=%s, reason=%s", result.status().intValue(), result.status().reason()));
                    }
                });
    }
}
