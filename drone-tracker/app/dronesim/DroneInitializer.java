package dronesim;

import akka.actor.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import domain.models.Coordinate;
import geocode.GeoName;
import geocode.ReverseGeoCode;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;


/** a wrapper actor intended to be used as a singleton to initialize drones */
public class DroneInitializer extends AbstractActor{
    public static Props props(ActorRef droneRegion) { return Props.create(DroneInitializer.class, droneRegion); }

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private ActorRef droneRegion;

    private int NUM_DRONES;
    private int NUM_RANDOM_CITIES;

    private Map<String, DroneProtocol.Init> initMessages = new HashMap<>();
    private Map<String, ActorRef> drones = new HashMap<>();

    /** message to lookup drone tracker */
    private static class LookupDroneTracker {}

    /** constructor */
    public DroneInitializer(ActorRef droneRegion) {
        Config config = context().system().settings().config();
        NUM_DRONES = config.getInt("drone-sim.num-drones");
        NUM_RANDOM_CITIES = config.getInt("drone-sim.num-random-cities");
        this.droneRegion = droneRegion;
    }

    /** actor init */
    @Override
    public void preStart() {
        initializeDrones();
    }

    /**
     * creates drones
     */
    private void initializeDrones() {
        List<GeoName> placeNames = loadPlaceNames();
        Collections.shuffle(placeNames);
        placeNames = placeNames.stream().limit(NUM_RANDOM_CITIES).collect(Collectors.toList());
        Random rand = new Random();
        ActorSystem system = getContext().getSystem();

        // create drones by sending init message to drone shard region to create drones across cluster
        log.info("initializing {} simulated drones", NUM_DRONES);
        for (int i = 0; i < NUM_DRONES; ++i) {
            String id = "drone-" + i;
            GeoName randomPlace =  placeNames.get(rand.nextInt(placeNames.size()));

            // randomize init to space out drone data send.  drones that have been already initialized will
            // ignore init message
            DroneProtocol.Init initMessage = new DroneProtocol.Init(id, new Coordinate(randomPlace.latitude, randomPlace.longitude));
            this.initMessages.put(initMessage.id, initMessage);
            system.getScheduler().scheduleOnce(Duration.ofSeconds(rand.nextInt(60)), this.droneRegion,
                    initMessage, system.dispatcher(), getSelf());
        }
    }

    /** loads set of place names from file */
    private ArrayList<GeoName> loadPlaceNames() {
        // load country filter if applicable
        Config config = getContext().getSystem().settings().config();
        List<String> countryList;
        try {
            countryList = config.getStringList("drone-sim.location-filter.country-codes");
        } catch (Exception ex) {
            countryList = null;
        }

        Set<String> countrySet;
        final boolean filterByCountry;
        if (null != countryList && countryList.size() > 0) {
            countrySet = new HashSet<>(countryList);
            filterByCountry = true;
        } else {
            countrySet = new HashSet<>();
            filterByCountry = false;
        }

        // load rgc database and extract place names
        ReverseGeoCode rgc = ReverseGeoCode.loadDefaultRGC();
        return Lists.newArrayList(
                Lists.newArrayList(rgc.getPlaceNames())
                        .stream()
                        .filter(geoName -> (!filterByCountry || countrySet.contains(geoName.country)))
                        .collect(Collectors.toList())
        );
    }

    /** message reciever */
    public AbstractActor.Receive createReceive() {
        return receiveBuilder()
                .matchAny(msg -> log.warning("received unknown message {}", msg))
                .build();
    }
}
