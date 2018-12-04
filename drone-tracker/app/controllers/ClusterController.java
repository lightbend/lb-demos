package controllers;

import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.cluster.MemberStatus;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class ClusterController extends Controller {
    private Cluster cluster;
    private final Set<MemberStatus> READY_STATES = new HashSet<>(Arrays.asList(
        MemberStatus.up(), MemberStatus.weaklyUp()
    ));
    private final Set<MemberStatus> ALIVE_STATES = new HashSet<>(Arrays.asList(
        MemberStatus.joining(), MemberStatus.weaklyUp(), MemberStatus.up(), MemberStatus.leaving(), MemberStatus.exiting()
    ));

    /**
     * constructor
     * @param system
     */
    @Inject
    public ClusterController(ActorSystem system) {
        this.cluster = Cluster.get(system);
    }

    /** health endpoint to indicate whether cluster is ready to receive traffic */
    public Result ready() {
        return checkHealth(READY_STATES);
    }

    /** health endpoint to indicate whether cluster is alive */
    public Result alive() {
        return checkHealth(ALIVE_STATES);
    }

    /** helper to check cluster health */
    protected Result checkHealth(Set<MemberStatus> healthSet) {
        MemberStatus status = cluster.selfMember().status();
        if (healthSet.contains(status)) {
            return ok();
        } else {
            return internalServerError(String.format("cluster state is " + status));
        }
    }

}
