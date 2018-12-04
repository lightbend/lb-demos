package dronesim;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.actor.Scheduler;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.MemberEvent;
import akka.cluster.Member;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import scala.concurrent.ExecutionContextExecutor;
import util.DemoDataUtil;
import util.MetricFactory;
import util.StringPair;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static dronesim.DroneProtocol.*;
import static domain.DeviceDataProcessorProtocol.*;
import static dronesim.SummaryReporterProtocol.*;
import static util.PlatformUtil.serviceLookup;

/**
 * simluates query for temperature data derived from drones.  one per node
 */
public class SummaryReporter extends AbstractActor {
    /** props function */
    public static Props props() { return Props.create(SummaryReporter.class); }

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final Duration DRONE_TRACKER_LOOKUP_WAIT;
    private final Duration REQUEST_TIMEOUT;
    private final Duration RANDOM_DELAY;
    private final Duration ORG_SUMMARY_INTERVAL;

    private String ORG_SUMMARY_LATENCY_METRIC = "org-summary-latency";
    private String ORG_SUMMARY_ERRORS_METRIC = "org-summary-errors";
    private String ERROR_LABEL = "error";

    private MetricFactory metricFactory = new MetricFactory(getContext());
    private ArrayList<String> orgNames;
    private ArrayList<String> orgNameSubset = new ArrayList<>();
    private Config droneTrackerConfig;
    private DroneTrackerClient droneTracker = null;

    private Cluster cluster = Cluster.get(getContext().getSystem());

    /** constructor */
    public SummaryReporter() {
        Config config = getContext().getSystem().settings().config();
        this.orgNames = DemoDataUtil.generateOrgNames(config.getInt("drone-tracker.pipelines.enrichment.num-orgs"));
        this.orgNames.sort(Comparator.comparing(String::toString));
        this.droneTrackerConfig = config.getConfig("drone-sim.drone-tracker.contact-point-discovery");
        DRONE_TRACKER_LOOKUP_WAIT = config.getDuration("drone-sim.drone-tracker.lookup-wait-interval");
        REQUEST_TIMEOUT = config.getDuration("drone-sim.drone-tracker.timeout");
        RANDOM_DELAY = config.getDuration("drone-sim.summary-reporter.random-delay");
        ORG_SUMMARY_INTERVAL = config.getDuration("drone-sim.summary-reporter.org-summary-interval");
    }

    /** init actor */
    @Override
    public void preStart() {
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), MemberEvent.class);
        scheduleLookupDroneTrackerService(Duration.ofMillis(0));
    }

    /** actor cleanup */
    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    /** initializes summary reporter state */
    protected void initReporterState() {
        // determine self index
        Member selfMember = this.cluster.selfMember();
        int selfIndex = 0;
        ArrayList<Member> members = Lists.newArrayList(this.cluster.state().getMembers());
        members.sort(Comparator.comparing(Member::toString));
        for (Member member: members) {
            if (member.equals(selfMember)) break;

            ++selfIndex;
        }

        if (selfIndex >= members.size()) {
            throw new RuntimeException(String.format("cannot find self member node %s in cluster member list", selfMember));
        }

        // assign orgs to this node
        ArrayList<String> myOrgNames = new ArrayList<>();
        for (int i = 0; i < this.orgNames.size(); ++i) {
            if ((i % members.size()) == selfIndex) {
                myOrgNames.add(this.orgNames.get(i));
            }
        }
        this.orgNameSubset = myOrgNames;
    }

    /** init reporter query behavior */
    public void becomeReporter() {
        getContext().become(reporterReceive());
    }

    /** schedule the loookup of the dronetracker service */
    private void scheduleLookupDroneTrackerService(Duration delay) {
        getContext().getSystem().getScheduler().scheduleOnce(delay, self(), new LookupDroneTrackerService(),
                getContext().getSystem().dispatcher(), getSelf());
    }

    /** schedule summary queries for org */
    private void scheduleOrgSummaryQueries(String org) {
        Scheduler scheduler = getContext().getSystem().getScheduler();
        ExecutionContextExecutor dispatcher = getContext().getSystem().dispatcher();
        Random random = new Random();

        scheduler.scheduleOnce(
                Duration.ofMillis(random.nextInt((int)RANDOM_DELAY.toMillis()) + ORG_SUMMARY_INTERVAL.toMillis()),
                getSelf(),
                new QueryOrgSummaryTick(org),
                dispatcher,
                getSelf()
        );
    }

    /** message receiver */
    @Override
    public Receive createReceive() {
        return memberEventReceive().orElse(receiveBuilder()
                .match(LookupDroneTrackerService.class, msg -> handleLookupDroneTracker())
                .match(DroneTrackerServiceFound.class, msg -> {
                    handleDroneTrackerServiceFound(msg.url);
                    becomeReporter();
                })
                .matchAny(msg -> log.warning("received unknown message {}", msg))
                .build());
    }

    /** message receiver in reporter mode */
    public Receive reporterReceive() {
        return memberEventReceive().orElse(receiveBuilder()
                .match(QueryOrgSummaryTick.class, m -> handleQueryOrgSummary(m.org))
                .matchAny(msg -> log.warning("received unknown message {}", msg))
                .build());
    }

    /** member event listener */
    public Receive memberEventReceive() {
        return receiveBuilder()
                .match(MemberEvent.class, msg -> initReporterState())
                .build();
    }

    /** initiate lookup drone tracker lookup */
    private void handleLookupDroneTracker() {
        serviceLookup(this.droneTrackerConfig, REQUEST_TIMEOUT, getContext().getSystem())
                .thenApply(url -> { self().tell(new DroneTrackerServiceFound(url), self()); return null; })
                .exceptionally(ex -> {
                    log.error(ex, "exception looking up droneTracker, will try again in %s ms", DRONE_TRACKER_LOOKUP_WAIT);
                    scheduleLookupDroneTrackerService(DRONE_TRACKER_LOOKUP_WAIT);
                    return null;
                });
    }

    /** handles finding of dronetracker service */
    private void handleDroneTrackerServiceFound(String url) {
        this.droneTracker = new DroneTrackerClient(url, REQUEST_TIMEOUT.toMillis(), getContext().getSystem());

        // schedule queries for city temperature and drone summary
        for (String org: this.orgNames) {
            scheduleOrgSummaryQueries(org);
        }
    }

    /** handle query cities by org */
    private void handleQueryOrgSummary(String org) {
        long queryStart = System.currentTimeMillis();
        // query for org city list
        this.droneTracker.getCities(org)
                // query each city temp in sequence
                .thenCompose(citiesByOrg -> {
System.out.format("QUERY CITIES: [org=%s] querying %d cities\n", org, citiesByOrg.cities.size());
                    if (citiesByOrg.cities.size() > 0) {
                        CompletionStage<AggregateCityTemperature> cityTemps = this.droneTracker.getTemperature(org, citiesByOrg.cities.get(0));
                        for (int i = 1; i < citiesByOrg.cities.size(); ++i) {
                            final int index = i;
System.out.format("QUERY TEMP: [org=%s] querying temp for %s\n", org, citiesByOrg.cities.get(index));
                            cityTemps = cityTemps
                                .thenCompose(x -> this.droneTracker.getTemperature(org, citiesByOrg.cities.get(index)));
                        }

                        return cityTemps.thenApply(x -> true);
                    } else {
                        return CompletableFuture.completedFuture(true);
                    }
                })
                // query drone summary for org
                .thenApply(x -> {
System.out.format("QUERY DRONE SUMMARY: [org=%s]\n", org);
                    return this.droneTracker.getDroneSummary(org);
                })
                // record summary time
                .thenApply(x -> {
                    this.metricFactory.getRecorder(ORG_SUMMARY_LATENCY_METRIC).record(System.currentTimeMillis() - queryStart);
                    return true;
                })
                // count errors if applicable
                .exceptionally(ex -> {
System.out.format("EXCEPTION: [org=%s] ex=%s\n", org, ex);
ex.printStackTrace();
                    this.metricFactory.getCounter(ORG_SUMMARY_ERRORS_METRIC, new StringPair(ERROR_LABEL, ex.getClass().getName())).increment();
                    return true;
                })
                .thenApply(x -> {
System.out.format("RESCHEDULING SUMMARY QUERIES: [org=%s]\n", org);
                    scheduleOrgSummaryQueries(org);
                    return true;
                });
    }
}
