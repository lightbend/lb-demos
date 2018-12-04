package domain;

import akka.actor.ActorRef;
import akka.cluster.sharding.ShardRegion;
import geocode.GeoName;

import javax.swing.text.html.Option;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static domain.DataEnrichmentProtocol.*;
import static domain.DeviceDataProcessorProtocol.*;

public class SummarizeCityTemperatureProtocol {
    /** message extractor for SummarizeByWorker messages */
    public static class SummarizeCityTemperatureExtractor implements ShardRegion.MessageExtractor {
        private int numShards;
        public SummarizeCityTemperatureExtractor(int numShards) { this.numShards = numShards; }

        private String  orgToShardId(String org) {
            return String.valueOf(Math.abs(org.hashCode()) % this.numShards);
        }

        @Override
        public String shardId(Object message) {
            if (message instanceof EnrichedMeasurement) {
                return orgToShardId(((EnrichedMeasurement) message).getOrg());
            } else if (message instanceof GetCitiesByOrg) {
                return orgToShardId(((GetCitiesByOrg)message).org);
            } else if (message instanceof GetCityTemperatureByOrg) {
                return orgToShardId(((GetCityTemperatureByOrg)message).org);
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


    /** retrieve list of cities we have data for */
    public static class GetCitiesByOrg implements Serializable {
        public String org;
        public GetCitiesByOrg() {}
        public GetCitiesByOrg(String org) {
            this.org = org;
        }

    }

    /** response to GetCities */
    public static class CitiesByOrg implements Serializable {
        public String org;
        public List<String> cities = new ArrayList<>();
        public CitiesByOrg(){}
        public CitiesByOrg(String org, Collection<String> cities) {
            this.org = org;
            this.cities.addAll(cities);
        }
    }

    /** query current city temperature */
    public static class GetCityTemperatureByOrg implements Serializable {
        public String org;
        public String city;
        public GetCityTemperatureByOrg() {}
        public GetCityTemperatureByOrg(String org, String city) {
            this.org = org;
            this.city = city;
        }
    }

    /** response to GetCityTemperature */
    public static class CityTemperatureByOrg implements Serializable {
        public String org;
        public String city;
        public Double temperature;
        public CityTemperatureByOrg() {}
        public CityTemperatureByOrg(String org, String city, Double temperature) {
            this.org = org;
            this.city = city;
            this.temperature = temperature;
        }
    }
}
