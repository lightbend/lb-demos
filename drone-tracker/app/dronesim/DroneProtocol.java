package dronesim;

import akka.cluster.sharding.ShardRegion;
import domain.models.Coordinate;

import java.io.Serializable;

public class DroneProtocol implements Serializable {
    /** create message extractor for drone shard region messages */
    public static class DroneRegionMessageExtractor implements ShardRegion.MessageExtractor {
        private int numShards;
        public DroneRegionMessageExtractor(int numShards) { this.numShards = numShards; }

        @Override
        public String shardId(Object message) {
            if (message instanceof Init)
                return String.valueOf(Math.abs(((Init)message).id.hashCode()) % this.numShards);
            else
                return null;
        }

        @Override
        public String entityId(Object message) {
            if (message instanceof Init)
                return ((Init)message).id;
            else
                return null;
        }

        @Override
        public Object entityMessage(Object message) {
            return message;
        }
    }

    /** initialization message to drone actor */
    public static class Init implements Serializable {
        public String id;
        public Coordinate startingPoint;
        public Init() {}
        public Init(String id, Coordinate startingPoint) {
            this.id = id;
            this.startingPoint = startingPoint;
        }
    }

    /** initialization response message */
    public static class Initialized implements Serializable {}

    // internal book keeping messages
    protected static class SendDataTick {}
    protected static class SampleDataTick {}
    protected static class OpTick {}
    protected static class AdjustDroneTargetTick {}
    protected static class RecordMetricsTick {}
    protected static class LookupDroneTrackerService {}
    protected static class DroneTrackerServiceFound {
        public String url;
        public DroneTrackerServiceFound(String url) { this.url = url; }
    }
}
