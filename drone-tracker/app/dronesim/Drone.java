package dronesim;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;
import domain.models.Coordinate;
import domain.models.DeviceDataPacket;
import domain.models.Measurement;

import util.GeoUtil;
import util.MetricFactory;
import util.RandomSampleGenerator;
import util.StringPair;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Random;

import static dronesim.DroneProtocol.*;
import static util.PlatformUtil.*;

public class Drone extends AbstractActor {
    /** Drone props */
    public static Props props() { return Props.create(Drone.class); }

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final Duration REQUEST_TIMEOUT;
    private final Duration DRONE_TRACKER_LOOKUP_WAIT;

    private String OP_INTERVAL = "op_interval";
    private String DATA_SAMPLE_INTERVAL = "data_sample_interval";
    private String DATA_SEND_INTERVAL = "data_send_interval";
    private String DATA_SEND_LATENCY = "data_send_latency";
    private String ACTUAL_OP_INTERVAL = "actual_op_interval";
    private String ACTUAL_DATA_SAMPLE_INTERVAL = "actual_data_sample_interval";
    private String ACTUAL_DATA_SEND_INTERVAL = "actual_data_send_interval";
    private String MEASUREMENTS_SENT = "measurements_sent";
    private String MEASUREMENTS_BATCH_SIZE = "measurements_batch_size";
    private String DATA_SEND_ERROR = "data_send_errors";
    private String ERROR_LABEL = "error";
    private MetricFactory metricFactory = new MetricFactory(getContext());

    private Init initMessage = null;

    private Duration sendInterval;
    private Duration sampleInterval;
    private Duration opInterval;
    private Duration adjustInterval;
    private Duration recordMetricsInterval;
    private String id = null;
    private Config droneTrackerConfig;
    private DroneTrackerClient droneTracker = null;

    private Coordinate curLocation;
    private float curSpeed;
    private float curHeading;
    private float curTemp;
    private long curTimestamp = System.currentTimeMillis();
    private long opTimestamp = System.currentTimeMillis();
    private long dataSampleTimestamp = System.currentTimeMillis();
    private long dataSendTimestamp = System.currentTimeMillis();
    private ArrayList<Measurement> measurements = new ArrayList<>();
    private RandomSampleGenerator headingDeltaSampleGen;
    private RandomSampleGenerator speedSampleGen;
    private RandomSampleGenerator tempDeltaSampleGen;
    private RandomSampleGenerator intervalSampleGen;

    /** constructor */
    public Drone() {
        Config config = getContext().getSystem().settings().config();
        REQUEST_TIMEOUT = config.getDuration("drone-sim.drone-tracker.timeout");
        DRONE_TRACKER_LOOKUP_WAIT = config.getDuration("drone-sim.drone-tracker.lookup-wait-interval");
        this.sendInterval = Duration.ofMillis(config.getLong("drone-sim.data-rate.send-interval-ms"));
        this.sampleInterval = Duration.ofMillis(config.getLong("drone-sim.data-rate.sample-interval-ms"));
        this.opInterval = Duration.ofMillis((long)(config.getDouble("drone-sim.behavior-control.op-freq-hz") / 60 * 1000));
        this.recordMetricsInterval = Duration.ofMillis(config.getLong("drone-sim.data-rate.record-metrics-interval-ms"));

        this.droneTrackerConfig = config.getConfig("drone-sim.drone-tracker.contact-point-discovery");
    }

    /** actor init */
    @Override
    public void preStart() {
        this.id = getSelf().path().name();
    }

    /** schedules OpTick */
    private void scheduleOpCycle() {
        long now = System.currentTimeMillis();
        this.metricFactory.getRecorder(ACTUAL_OP_INTERVAL).record(now - this.opTimestamp);
        this.opTimestamp = now;
        getContext().getSystem().getScheduler().scheduleOnce(this.opInterval, getSelf(), new OpTick(),
                getContext().getSystem().dispatcher(), getSelf());
    }

    /** schedules AdjustDroneTargetTick */
    private void scheduleDroneAdjustCycle() {
        getContext().getSystem().getScheduler().scheduleOnce(this.adjustInterval, getSelf(), new AdjustDroneTargetTick(),
                getContext().getSystem().dispatcher(), getSelf());
    }

    /** schedules SampleDataTick */
    private void scheduleSampleData() {
        long now = System.currentTimeMillis();
        this.metricFactory.getRecorder(ACTUAL_DATA_SAMPLE_INTERVAL).record(now - this.dataSampleTimestamp);
        this.dataSampleTimestamp = now;
        getContext().getSystem().getScheduler().scheduleOnce(this.sampleInterval, getSelf(), new SampleDataTick(),
                getContext().getSystem().dispatcher(), getSelf());
    }

    /** schedules SendDataTick */
    private void scheduleSendData() {
        long now = System.currentTimeMillis();
        this.metricFactory.getRecorder(ACTUAL_DATA_SEND_INTERVAL).record(now - this.dataSendTimestamp);
        this.dataSendTimestamp = now;
        getContext().getSystem().getScheduler().scheduleOnce(this.sendInterval, getSelf(), new SendDataTick(),
                getContext().getSystem().dispatcher(), getSelf());
    }

    /** schedule the loookup of the dronetracker service */
    private void scheduleLookupDroneTrackerService(Duration delay) {
        getContext().getSystem().getScheduler().scheduleOnce(delay, getSelf(), new LookupDroneTrackerService(),
                getContext().getSystem().dispatcher(), getSelf());
    }

    /** schedules RecordMetricsTick */
    private void scheduleRecordMetrics() {
        getContext().getSystem().getScheduler().scheduleOnce(this.recordMetricsInterval, getSelf(), new RecordMetricsTick(),
                getContext().getSystem().dispatcher(), getSelf());
    }

    /** initializes drone */
    private void handleInit(Init init) {
        if (this.initMessage == null) {
            this.initMessage = init;

            // load randomization generators
            RandomSampleGenerator locationRadiusGen = loadSampleGen("initial-location-radius-m");
            RandomSampleGenerator speedGen = loadSampleGen("speed-mps");
            RandomSampleGenerator headingDeltaGen = loadSampleGen("heading-delta-deg");
            RandomSampleGenerator tempGen = loadSampleGen("temp-c");
            RandomSampleGenerator tempDeltaGen = loadSampleGen("temp-delta-c");
            RandomSampleGenerator adjustIntervalGen = loadSampleGen("adjust-interval-ms");

            // initialize initial drone state
            Random random = new Random();
            this.curHeading = random.nextFloat() * 360;
            this.curSpeed = speedGen.nextSample();
            this.curTemp = tempGen.nextSample();
            float dist = locationRadiusGen.nextSample();
            this.curLocation = GeoUtil.calcDesCoordWithHeadingAndDistance(init.startingPoint, this.curHeading, dist);
            this.curTimestamp = System.currentTimeMillis();
            this.adjustInterval = Duration.ofMillis((long)adjustIntervalGen.nextSample());

            // save sample generators
            this.speedSampleGen = speedGen;
            this.headingDeltaSampleGen = headingDeltaGen;
            this.tempDeltaSampleGen = tempDeltaGen;
            this.intervalSampleGen = adjustIntervalGen;

            // set schedulers for drone operation and data collection
            scheduleOpCycle();
            scheduleDroneAdjustCycle();
            scheduleSampleData();
            scheduleSendData();
            scheduleRecordMetrics();
            scheduleLookupDroneTrackerService(Duration.ofMillis(0));
        }
    }

    /** initiate lookup drone tracker lookup */
    private void lookupDroneTracker() {
        serviceLookup(this.droneTrackerConfig, REQUEST_TIMEOUT, getContext().getSystem())
                .thenApply(url -> { getSelf().tell(new DroneTrackerServiceFound(url), getSelf()); return null; })
                .exceptionally(ex -> {
                    log.error(ex, "exception looking up droneTracker, will try again in %s ms", DRONE_TRACKER_LOOKUP_WAIT);
                    scheduleLookupDroneTrackerService(DRONE_TRACKER_LOOKUP_WAIT);
                    return null;
                });
    }

    /**
     * helper to load sample generator
     * @param controlKey
     * @return
     */
    private RandomSampleGenerator loadSampleGen(String controlKey) {
        Config config = getContext().getSystem().settings().config();
        return new RandomSampleGenerator(config.getConfig(String.format("drone-sim.behavior-control.%s", controlKey)));
    }

    /** message receiver */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Init.class, msg -> handleInit(msg))
                .match(LookupDroneTrackerService.class, msg -> lookupDroneTracker())
                .match(DroneTrackerServiceFound.class, msg -> handleDroneTrackerServiceFound(msg.url))
                .match(OpTick.class, msg -> handleOpCycle())
                .match(AdjustDroneTargetTick.class, msg -> handleAdjustDroneTarget())
                .match(SampleDataTick.class, msg -> handleSampleData())
                .match(SendDataTick.class, msg -> handleSendData())
                .match(RecordMetricsTick.class, msg -> handleRecordMetrics())
                .matchAny(msg -> log.warning("received unknown message {}", msg))
                .build();
    }

    /** sends data to drone tracker service */
    private void handleOpCycle() {
        long newTimestamp = System.currentTimeMillis();
        float distance = this.curSpeed * (newTimestamp - this.curTimestamp) / 1000;
        this.curLocation = GeoUtil.calcDesCoordWithHeadingAndDistance(this.curLocation, this.curHeading, distance);
        this.curTemp += this.tempDeltaSampleGen.nextSample();
        this.curTimestamp = newTimestamp;

        scheduleOpCycle();
    }

    /** sends data to drone tracker service */
    private void handleAdjustDroneTarget() {
        float headingDelta = this.headingDeltaSampleGen.nextSample();
        this.curHeading += headingDelta;
        this.curSpeed = this.speedSampleGen.nextSample();
        scheduleDroneAdjustCycle();
    }

    /** sends data to drone tracker service */
    private void handleSampleData() {
        this.measurements.add(new Measurement(System.currentTimeMillis(), this.curLocation, this.curTemp));
        scheduleSampleData();
    }

    /** handles finding of dronetracker service */
    private void handleDroneTrackerServiceFound(String url) {
        this.droneTracker = new DroneTrackerClient(url, REQUEST_TIMEOUT.toMillis(), getContext().getSystem());
    }

    /** sends data to drone tracker service */
    private void handleSendData() {
        if (this.droneTracker != null && this.measurements.size() > 0) {
            long startTime = System.currentTimeMillis();
            final long numMeasurements = this.measurements.size();
            this.droneTracker.sendData(new DeviceDataPacket(this.id, System.currentTimeMillis(), this.measurements))
                    .handle((response, ex) -> {
                        if (ex != null) {
                            this.metricFactory.getCounter(DATA_SEND_ERROR, new StringPair(ERROR_LABEL, ex.getClass().getName())).increment();
                            log.error(ex, "exception when sending data to drone tracker {}", ex);
                        } else if (response.status().intValue() != 201) {
                            this.metricFactory.getRecorder(DATA_SEND_LATENCY).record(System.currentTimeMillis() - startTime);
                            this.metricFactory.getCounter(DATA_SEND_ERROR, new StringPair(ERROR_LABEL, "status:" + response.status())).increment();
                            log.error("unexpected response when sending data to drone tracker{}", response.status());
                        } else {
                            this.metricFactory.getRecorder(DATA_SEND_LATENCY).record(System.currentTimeMillis() - startTime);
                            this.metricFactory.getCounter(MEASUREMENTS_SENT).increment(numMeasurements);
                            this.metricFactory.getGaugeLong(MEASUREMENTS_BATCH_SIZE).set(numMeasurements);
                        }
                        return null;
                    });
        } else {
            log.warning("drone tracker has not been resolved yet, skipping data send cycle");
        }

        this.measurements = new ArrayList<>();
        scheduleSendData();
    }

//    /** get counter metric, create if it doesn't already exist */
//    private Counter getSendErrorCounter(String errorType) {
//        if (DATA_SEND_ERRORS_TABLE.containsKey(errorType)) {
//            return DATA_SEND_ERRORS_TABLE.get(errorType);
//        } else {
//            HashMap<String, String> tags = new HashMap<>();
//            tags.put("error", errorType);
//            Counter counter = CinnamonMetrics.get(getContext()).createCounter(DATA_SEND_ERROR_NAME, tags);
//            DATA_SEND_ERRORS_TABLE.put(errorType, counter);
//            return counter;
//        }
//    }

    /** records drone metrics for monitoring */
    private void handleRecordMetrics() {
        this.metricFactory.getGaugeLong(OP_INTERVAL).set(this.opInterval.toMillis());
        this.metricFactory.getGaugeLong(DATA_SAMPLE_INTERVAL).set(this.sampleInterval.toMillis());
        this.metricFactory.getGaugeLong(DATA_SEND_INTERVAL).set(this.sendInterval.toMillis());
        scheduleRecordMetrics();
    }
}
