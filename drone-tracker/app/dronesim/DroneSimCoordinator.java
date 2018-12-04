package dronesim;

import akka.actor.*;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.cluster.sharding.ShardRegion;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.DeciderBuilder;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import domain.models.Coordinate;
import dronesim.DroneProtocol;
import geocode.GeoName;
import geocode.ReverseGeoCode;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.Type;


import javax.inject.Inject;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static util.PlatformUtil.*;


public class DroneSimCoordinator extends AbstractActor {
    /** props function */
    public static Props props() {
        return Props.create(DroneSimCoordinator.class);
    }

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private int NUM_SHARDS;

    // drone shard region
    private ActorRef droneRegion;

    private ActorRef droneInitializer;

    private ActorRef summaryReporter;

    /** constructor */
    @Inject
    public DroneSimCoordinator() {
        Config config = context().system().settings().config();
        NUM_SHARDS = config.getInt("drone-sim.num-shards");
    }

    /** actor init */
    @Override
    public void preStart() {
        // initialize drone shard region
        ActorSystem system = getContext().getSystem();
        ClusterShardingSettings shardingSettings = ClusterShardingSettings.create(system);
        this.droneRegion = ClusterSharding.get(system).start(Drone.class.getName(), Drone.props(), shardingSettings,
                new DroneProtocol.DroneRegionMessageExtractor(NUM_SHARDS));

        // use singleton drone initializer to initialize drones
        final ClusterSingletonManagerSettings singletonSettings = ClusterSingletonManagerSettings.create(system);
        this.droneInitializer = system.actorOf(ClusterSingletonManager.props(
            dronesim.DroneInitializer.props(this.droneRegion),
            PoisonPill.getInstance(),
            singletonSettings
        ));

        // create summary reporter
        this.summaryReporter = getContext().actorOf(SummaryReporter.props());
    }

    /** message receiver */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .matchAny(msg -> log.warning("received unknown message {}", msg))
                .build();
    }
}
