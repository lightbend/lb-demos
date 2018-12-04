package dronesim;

public class SummaryReporterProtocol {

    // internal query invocation messages
    protected static class QueryOrgSummaryTick {
        public String org;
        public QueryOrgSummaryTick() {}
        public QueryOrgSummaryTick(String org) { this.org = org; }
    }

//    protected static class QueryCityTempTick {}

}
