package dronesim;

import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DroneSimStartup {
    @Inject
    public DroneSimStartup(ActorSystem system) {

        system.actorOf(DroneSimCoordinator.props(), "drone-sim-coordinator");
    }
}
