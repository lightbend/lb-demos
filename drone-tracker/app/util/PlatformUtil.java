package util;

import akka.actor.ActorSystem;
import akka.discovery.Lookup;
import akka.discovery.ServiceDiscovery;
import akka.discovery.SimpleServiceDiscovery;
import com.typesafe.config.Config;
import dronesim.ServiceUnresolved;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.Type;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PlatformUtil {
    /**
     * looks up service using dns srv records
     * @param contactPointConfig
     */
    public static String serviceLookupSync(Config contactPointConfig) {
        String manualUrl;
        try{
            manualUrl = contactPointConfig.getString("manual-url");
        } catch (Exception ex) {
            manualUrl = "";
        }
        if (manualUrl != null) {
            return manualUrl;
        } else {
            String serviceName = contactPointConfig.getString("service-name");
            String namespace = contactPointConfig.getString("service-namespace");
            String portName = contactPointConfig.getString("port-name");
            String protocol = contactPointConfig.getString("protocol");
            String query = String.format("_%s._%s.%s.%s", portName, protocol, serviceName, namespace);
            try {
                Record[] records = new org.xbill.DNS.Lookup(query, Type.SRV).run();
                if (records != null && records.length > 0) {
                    SRVRecord record = (SRVRecord)records[(new Random()).nextInt(records.length)];
                    return String.format("http://%s:%s", record.getTarget().toString(true), record.getPort());
                } else {
                    throw new RuntimeException(String.format("service lookup for '%s' resolved 0 records", query));
                }

            } catch (Exception ex) {
                throw new RuntimeException("exception looking up DNS SRV with DNSJava: " + ex, ex);
            }
        }
    }

    /**
     * uses akka service discovery to lookup a service endpoint
     * @param contactPointConfig
     * @param system
     * @return endpoint as 'http://ip:port'
     */
    public static CompletionStage<String> serviceLookup(Config contactPointConfig, Duration timeout, ActorSystem system) {
        String manualUrl;
        try{
            manualUrl = contactPointConfig.getString("manual-url");
        } catch (Exception ex) {
            manualUrl = "";
        }
        if (manualUrl.length() != 0) {
            return CompletableFuture.completedFuture(manualUrl);
        } else {
            String serviceName = contactPointConfig.getString("service-name");
            String namespace = contactPointConfig.getString("service-namespace");
            String portName = contactPointConfig.getString("port-name");
            String protocol = contactPointConfig.getString("protocol");

            SimpleServiceDiscovery discovery = ServiceDiscovery.get(system).discovery();
            Lookup lookup = Lookup.create(serviceName + "." + namespace)
                    .withPortName(portName)
                    .withProtocol(protocol);
            return discovery.lookup(lookup, timeout).thenApply(resolved -> {
                if (resolved.addresses().size() > 0) {
                    SimpleServiceDiscovery.ResolvedTarget target = resolved.addresses().head();
                    return String.format("http://%s:%s", target.host(), target.port().get());
                } else {
                    throw new ServiceUnresolved(String.format("service %s resolved to 0 addresses", serviceName));
                }
            });
        }
    }
}
