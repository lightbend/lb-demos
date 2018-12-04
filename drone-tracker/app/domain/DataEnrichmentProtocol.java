package domain;

import akka.actor.ActorRef;
import akka.cluster.sharding.ShardRegion;
import domain.models.DeviceDataPacket;
import domain.models.Measurement;

import geocode.GeoName;
import util.StreamUtil;

import java.io.Serializable;
import java.util.List;

/** messages for data processing pipeline */
public class DataEnrichmentProtocol {

    /** message extractor for DataEnrichmentWorker messages */
    public static class DataEnrichmentMessageExtractor implements ShardRegion.MessageExtractor {
        private int numShards;
        public DataEnrichmentMessageExtractor(int numShards) { this.numShards = numShards; }

        @Override
        public String shardId(Object message) {
            if (message instanceof DeviceDataPacket)
                return String.valueOf(Math.abs(((DeviceDataPacket)message).getId().hashCode()) % this.numShards);
            else
                return null;
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

    /** enriched measurement */
    public static class EnrichedMeasurement extends Measurement {
        private GeoName geoName;
        private String org;
        private String id;

        public EnrichedMeasurement(Measurement measurement) {
            super(measurement);
        }

        public EnrichedMeasurement withId(String id) {
            this.id = id;
            return this;
        }

        public EnrichedMeasurement withGeoName(GeoName name) {
            this.geoName = name;
            return this;
        }

        public EnrichedMeasurement withOrg(String org) {
            this.org = org;
            return this;
        }

        /** copy instance */
        public EnrichedMeasurement copy() {
            return new EnrichedMeasurement(this)
                    .withId(this.id)
                    .withGeoName(this.geoName)
                    .withOrg(this.org);
        }

        public GeoName getGeoName() {
            return geoName;
        }

        public void setGeoName(GeoName geoName) {
            this.geoName = geoName;
        }

        public String getOrg() {
            return org;
        }

        public void setOrg(String org) {
            this.org = org;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String toString() {
            return String.format("EnrichedMeasurement(id=%s, org=%s, geoname=%s, timestamp=%s, location=%s, temperature=%.4f",
                    getId(), getOrg(), getGeoName(), getTimestamp(), getLocation(), getTemperature());
        }

        /** helper function to average measurements by temperature */
        public static EnrichedMeasurement averageByTemperature(List<EnrichedMeasurement> measurements) {
            EnrichedMeasurement firstMeasurement = ((EnrichedMeasurement)measurements.get(0)).copy();
            float sumTemp = 0.0f;
            for (StreamUtil.WithTimestamp m: measurements) {
                sumTemp += ((EnrichedMeasurement)m).getTemperature();
            }
            firstMeasurement.setTemperature(sumTemp / measurements.size());
            return firstMeasurement;
        }
    }
}
