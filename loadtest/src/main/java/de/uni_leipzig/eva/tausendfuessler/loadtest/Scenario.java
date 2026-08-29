package de.uni_leipzig.eva.tausendfuessler.loadtest;

import java.time.Instant;

public interface Scenario {

    /** Everything a scenario needs: the API client, the CLI options and the moment the client process started. */
    record Context(CoordinatorApi api, Options options, Instant clientStart) {}

    String name();

    String nfa();

    ScenarioResult run(Context context) throws Exception;
}
