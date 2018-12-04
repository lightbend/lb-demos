package util;

import domain.models.Coordinate;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;

public class GeoUtil {

    /**
     * calculates destination coordinte by project starting point along given heading for given distance
     * @param start
     * @param heading
     * @param distance
     * @return
     */
    public static Coordinate calcDesCoordWithHeadingAndDistance(Coordinate start, float heading, float distance) {
        GeodesicData data = Geodesic.WGS84.Direct(start.getLat(), start.getLon(), heading, distance);
        return new Coordinate(data.lat2, data.lon2);
    }
}
