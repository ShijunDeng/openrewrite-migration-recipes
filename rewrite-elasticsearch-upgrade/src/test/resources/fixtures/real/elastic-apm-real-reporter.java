package co.elastic.apm.agent.esrestclient.v6_4;

import org.testcontainers.elasticsearch.ElasticsearchContainer;

class ElasticsearchRestClientInstrumentationITRealReporterFixture {
    private static ElasticsearchContainer container;

    static void startElasticsearchContainerAndClient() {
        container = new ElasticsearchContainer();
    }

    static void stopElasticsearchContainerAndClient() {
        container.stop();
    }
}
