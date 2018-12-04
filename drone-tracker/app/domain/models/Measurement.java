package domain.models;

import com.google.common.base.Objects;
import util.StreamUtil.WithTimestamp;

import java.io.Serializable;

public class Measurement implements WithTimestamp, Serializable {
    private long timestamp;
    private float temperature;
    private Coordinate location;

    /** constructor */
    public Measurement() {
    }

    /** copy constructor */
    public Measurement(Measurement that) {
        this.timestamp = that.timestamp;
        this.temperature = that.temperature;
        this.location = that.location;
    }

    /**
     * constructor
     * @param timestamp
     * @param temperature
     */
    public Measurement(long timestamp, Coordinate location, float temperature) {
        this.timestamp = timestamp;
        this.temperature = temperature;
        this.location = location;
    }

    /** @return timestamp in milliseconds since epoch */
    public long getTimestamp() {
        return timestamp;
    }

    /** @param timestamp */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /** @return latitude */
    public double getTemperature() {
        return this.temperature;
    }

    /** @param temperature */
    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    /** @param location */
    public void setLocation(Coordinate location) { this.location = location; }

    /** @return location */
    public Coordinate getLocation() { return this.location; }

    /** @see java.lang.Object#equals(java.lang.Object) */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Measurement that = (Measurement) o;
        return timestamp == that.timestamp &&
                Float.compare(that.temperature, temperature) == 0 &&
                that.location.equals(location);
    }

    /** @see java.lang.Object#hashCode() */
    @Override
    public int hashCode() {
        return Objects.hashCode(timestamp, temperature, location);
    }
}
