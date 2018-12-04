package domain;

import akka.cluster.sharding.ShardRegion;
import dronesim.DroneTrackerClient;
import geocode.GeoName;

import java.io.Serializable;
import java.util.*;

import static domain.DataEnrichmentProtocol.*;

public class SummarizeDronesProtocol {
    /** message extractor for SummarizeByWorker messages */
    public static class SummarizeDronesExtractor implements ShardRegion.MessageExtractor {
        private int numShards;
        public SummarizeDronesExtractor(int numShards) { this.numShards = numShards; }

        private String orgToShardId(String org) {
            return String.valueOf(Math.abs(org.hashCode()) % this.numShards);
        }

        @Override
        public String shardId(Object message) {
            if (message instanceof EnrichedMeasurement) {
                return orgToShardId(((EnrichedMeasurement) message).getOrg());
            } else if (message instanceof GetDroneSummary) {
                return orgToShardId(((GetDroneSummary)message).org);
            } else {
                return null;
            }
        }

        @Override
        public String entityId(Object message) {
            // one entity per shard
            return shardId(message);
        }

        @Override
        public Object entityMessage(Object message) {
            return message;
        }
    }

    /** get drone summary message */
    public static class GetDroneSummary implements Serializable {
        public String org;
        public GetDroneSummary() {}
        public GetDroneSummary(String org) {
            this.org = org;
        }
    }

    /** drone summary info */
    public static class DroneSummary implements Serializable {
        public String org;
        public Set<String> droneIds;
        public Map<String, Set<String>> dronesPerCity;

        /** constructor */
        public DroneSummary() {}

        /** constructor */
        public DroneSummary(String org, Set<String> droneIds, Map<String, Set<String>> dronesPerCity) {
            this.org = org;
            this.droneIds = droneIds;
            this.dronesPerCity = dronesPerCity;
        }

        /** copy constructor */
        public DroneSummary clone() {
            Set<String> newDroneIds = new HashSet<>();
            newDroneIds.addAll(this.droneIds);
            Map<String, Set<String>> newDronesPerCity = new HashMap<>();
            for (String city: this.dronesPerCity.keySet()) {
                Set<String> droneIds = new HashSet<>();
                droneIds.addAll(this.dronesPerCity.get(city));
                newDronesPerCity.put(city, droneIds);
            }

            return new DroneSummary(this.org, newDroneIds, newDronesPerCity);
        }
    }
}
