package modules;

import akka.actor.ActorSystem;
import akka.management.AkkaManagement;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import com.typesafe.config.Config;
import play.inject.ApplicationLifecycle;

import javax.inject.*;
import java.util.concurrent.CompletableFuture;

@Singleton
public class Startup {
    @Inject
    public Startup(ActorSystem system, ApplicationLifecycle lifecycle) {
        Config config = system.settings().config();
        if (config.getBoolean("akka.cluster.enable-cluster-bootstrap")) {
            // initialize cluster bootstrap
            AkkaManagement.get(system).start();
            ClusterBootstrap.get(system).start();
        }

        // shut-down hook
        lifecycle.addStopHook(() -> {
            return CompletableFuture.completedFuture(null);
        });
    }
}
