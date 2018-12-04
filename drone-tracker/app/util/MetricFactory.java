package util;

import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import com.lightbend.cinnamon.akka.CinnamonMetrics;
import com.lightbend.cinnamon.metric.*;

import java.util.*;

public class MetricFactory {
    private CinnamonMetrics cinnamon;
    private Map<String, Metric> metrics = new HashMap<>();

    /** constructor */
    public MetricFactory(ActorContext context) {
        this.cinnamon = CinnamonMetrics.get(context);
    }

    /** constructor */
    public MetricFactory(ActorSystem system) {
        this.cinnamon = CinnamonMetrics.get(system);
    }

    /**
     * constructs tagmap from tag pairs
     * @param tags
     * @return
     */
    private Map<String, String> toTagMap(StringPair... tags) {
        Map<String, String> tagMap = new HashMap<>();
        for (StringPair tagPair: tags) {
            tagMap.put(tagPair.key, tagPair.value);
        }
        return tagMap;
    }

    /**
     * generates uid for metric
     * @param name
     * @param tags
     * @return
     */
    private String metricKey(String name, StringPair... tags) {
        Optional<String> key = Arrays.stream(tags)
                .map(pair -> String.format("%s=%s", pair.key, pair.value))
                .sorted()
                .reduce((str, acc) -> str + "," + acc);
        return key.isPresent() ? name + "/" + key.get() : name;
    }

    /**
     * create metric of given type
     * @param type
     * @param name
     * @param tags
     * @param <T>
     * @return
     */
    private <T extends Metric> Metric createMetricOfType(Class<T> type, String name, StringPair... tags) {
        if (type == Counter.class) {
            return this.cinnamon.createCounter(name, toTagMap(tags));
        } else if (type == GaugeLong.class) {
            return this.cinnamon.createGaugeLong(name, toTagMap(tags));
        } else if (type == GaugeDouble.class) {
            return this.cinnamon.createGaugeDouble(name, toTagMap(tags));
        } else if (type == Recorder.class) {
            return this.cinnamon.createRecorder(name, toTagMap(tags));
        } else {
            throw new RuntimeException(String.format("no creator for metric type %s", type));
        }
    }

    /**
     * retrieves a metric or creates metrics
     * @param type
     * @param name
     * @param tags
     * @param <T>
     * @return
     */
    private <T extends Metric> T getMetric(Class<T> type, String name, StringPair... tags) {
        String metricKey = metricKey(name, tags);
        Metric metric = this.metrics.get(metricKey);
        if (metric != null) {
            return type.cast(metric);
        } else {
            Metric newMetric = createMetricOfType(type, name, tags);
            this.metrics.put(metricKey, newMetric);
            return type.cast(newMetric);
        }
    }

    /**
     * gets or creates a new counter
     * @param name
     * @param tags
     * @return
     */
    public Counter getCounter(String name, StringPair... tags) {
        return getMetric(Counter.class, name, tags);
    }

    /**
     * gets or creates a new gauge
     * @param name
     * @param tags
     * @return
     */
    public GaugeDouble getGaugeDouble(String name, StringPair... tags) {
        return getMetric(GaugeDouble.class, name, tags);
    }

    /**
     * gets or creates a new gauge
     * @param name
     * @param tags
     * @return
     */
    public GaugeLong getGaugeLong(String name, StringPair... tags) {
        return getMetric(GaugeLong.class, name, tags);
    }

    /**
     * gets or creates a new recorder
     * @param name
     * @param tags
     * @return
     */
    public Recorder getRecorder(String name, StringPair... tags) {
        return getMetric(Recorder.class, name, tags);
    }

}
