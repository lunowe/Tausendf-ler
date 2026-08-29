package de.uni_leipzig.eva.tausendfuessler.loadtest;

public interface Scenario {

    /** Everything a scenario needs: the API client and the CLI options. */
    record Context(CoordinatorApi api, Options options) {}

    String name();

    String nfa();

    ScenarioResult run(Context context) throws Exception;
}
