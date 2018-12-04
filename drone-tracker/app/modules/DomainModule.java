package modules;

import com.google.inject.AbstractModule;
import domain.DeviceDataProcessor;
import play.libs.akka.AkkaGuiceSupport;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;

public class DomainModule extends AbstractModule implements AkkaGuiceSupport {
    @Override
    protected void configure() {

        bindActor(DeviceDataProcessor.class, "device-data-processor");
        bind(Startup.class).asEagerSingleton();

    }
}
