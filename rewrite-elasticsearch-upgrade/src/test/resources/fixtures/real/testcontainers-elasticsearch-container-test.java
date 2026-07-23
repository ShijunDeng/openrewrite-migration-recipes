package org.testcontainers.elasticsearch;

class ElasticsearchContainerTestFixture {
    void elasticsearchDefaultTest() {
        try (ElasticsearchContainer container = new ElasticsearchContainer().withEnv("foo", "bar")) {
            container.start();
        }
    }

    void transportClientClusterHealth() {
        try (ElasticsearchContainer container = new ElasticsearchContainer()) {
            container.start();
        }
    }
}
