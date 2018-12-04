package dronesim;

import akka.actor.ActorSystem;
import akka.japi.Pair;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import domain.DeviceDataProcessorProtocol.AggregateCityTemperature;
import org.junit.*;
import play.libs.Json;

import java.util.Optional;

import static org.junit.Assert.*;

public class DroneTrackerClientTest {
    private String baseUrl = "http://0.0.0.0:9000";
    private ActorSystem system;
    private DroneTrackerClient client;

    @Before
    public void setUp() {
        this.system = ActorSystem.create("testActorSystem");
        this.client = new DroneTrackerClient(this.baseUrl,1000l, this.system);
    }

    @After
    public void tearDown() {
        this.system.terminate();
    }

    @Test
    public void testUri() {
        assertEquals(
                this.client.uri("my/path", Pair.create("org", "Org 1"), Pair.create("city", "san francisco")),
                String.format("%s/my/path?org=Org+1&city=san+francisco", this.baseUrl)
        );
    }

    @Test
    public void testMarshalAggregateCityTemperature() throws Exception {
        AggregateCityTemperature aggTemp = new AggregateCityTemperature("org1", "city1", 1.1);
        String aggTempStr = "{\"org\":\"org1\",\"city\":\"city1\",\"cityTemperature\":1.1}";
        assertEquals(Json.toJson(aggTemp).toString(), aggTempStr);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        AggregateCityTemperature unmarshaled = mapper.readValue(aggTempStr, AggregateCityTemperature.class);
        assertEquals(Json.toJson(unmarshaled).toString(), aggTempStr);
    }
}
