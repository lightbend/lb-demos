package domain.models;

import java.io.Serializable;
import java.util.List;

/**
 * represents a data packet sent by a device on a periodic basis
 */
public class DeviceDataPacket implements Serializable {
    private String id;
    private long timestamp;
    private List<Measurement> measurements;

    /** constructor */
    public DeviceDataPacket() {
    }

    /**
     * constructor
     * @param id
     * @param timestamp
     * @param measurements
     */
    public DeviceDataPacket(String id, long timestamp, List<Measurement> measurements) {
        this.id = id;
        this.timestamp = timestamp;
        this.measurements = measurements;
    }

    /** @return id */
    public String getId() {
        return id;
    }

    /** @param id */
    public void setId(String id) {
        this.id = id;
    }

    /** @return data package timestamp in millis */
    public long getTimestamp() {
        return this.timestamp;
    }

    /** @param timestamp */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /** @return location device measurements*/
    public List<Measurement> getMeasurements() {
        return this.measurements;
    }

    /** @param measurements */
    public void setMeasurements(List<Measurement> measurements) {
        this.measurements = measurements;
    }


}
