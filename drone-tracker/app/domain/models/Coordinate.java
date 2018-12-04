package domain.models;

import com.google.common.base.Objects;

import java.io.Serializable;

public class Coordinate implements Serializable {
    private double lat;
    private double lon;

    /** constructor */
    public Coordinate() {
        this(0, 0);
    }

    /** constructor */
    public Coordinate(double lat, double lon) {
        setLat(lat);
        setLon(lon);
    }

    /** constructor */
    public Coordinate(double[] point) {
        setLon(point[0]);
        setLat(point[1]);
    }

    /** @return lat */
    public double getLat() {
        return lat;
    }

    /** @param lat */
    public void setLat(double lat) {
        this.lat = lat;
    }

    /** @return lon */
    public double getLon() {
        return lon;
    }

    /** @param lon */
    public void setLon(double lon) {
        this.lon = lon;
    }

    /** @see java.lang.Object#equals(java.lang.Object) */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Coordinate that = (Coordinate) o;
        return Double.compare(that.lat, lat) == 0 &&
                Double.compare(that.lon, lon) == 0;
    }

    /** @see Object#hashCode() */
    @Override
    public int hashCode() {
        return Objects.hashCode(lat, lon);
    }

    /** @see Object#toString() */
    public String toString() {
        return String.format("[%.6f, %.6f]", this.lat, this.lon);
    }
}
