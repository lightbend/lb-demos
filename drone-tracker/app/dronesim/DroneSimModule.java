package dronesim;

import com.google.inject.AbstractModule;
import play.libs.akka.AkkaGuiceSupport;

public class DroneSimModule extends AbstractModule implements AkkaGuiceSupport {
    @Override
    protected void configure() {
        bind(DroneSimStartup.class).asEagerSingleton();
    }
}
