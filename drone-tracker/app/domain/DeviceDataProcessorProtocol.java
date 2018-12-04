package domain;

import java.io.Serializable;
import java.util.Optional;

public class DeviceDataProcessorProtocol {
    /** response message to DeviceDataPackage message*/
    public static class DataPacketResponse {
        public final long sampleInterval;
        public final long pushInterval;

        public DataPacketResponse(long sampleInterval, long pushInterval) {
            this.sampleInterval = sampleInterval;
            this.pushInterval = pushInterval;
        }
    }

//    /** get cities in summary */
//    public static class GetCities implements Serializable {
//        public String org;
//
//        public GetCities() { this.org = ""; }
//        public GetCities(String org) {
//            this.org = org;
//        }
//    }
//
//    /** city list result */
//    public static class Cities implements Serializable {
//        public List<String> cities = new ArrayList<>();
//
//        public Cities() {}
//        public Cities(Collection<String> cities) {
//            this.cities.addAll(cities);
//        }
//    }

    /** get temperature for a given city */
    public static class GetAggregatedTemperature implements Serializable {
        public String org = "";
        public String city = "";

        public GetAggregatedTemperature() {}
        public GetAggregatedTemperature(String org, String city) {
            this.org = org;
            this.city = city;
        }
    }

    /** temperature for given city */
    public static class AggregateCityTemperature implements Serializable {
        public String org;
        public String city;
        public Double cityTemperature;

        public AggregateCityTemperature() {}
        public AggregateCityTemperature(String org, String city, Double cityTemperature) {
            this.org = org;
            this.city = city;
            this.cityTemperature = cityTemperature;
        }
    }

    /** unexpected error */
    public static class UnexpectedError extends RuntimeException implements Serializable {
        public UnexpectedError() {}
        public UnexpectedError(String message) { super(message); }
        public UnexpectedError(String message, Throwable error) { super(message, error); }
    }
}
