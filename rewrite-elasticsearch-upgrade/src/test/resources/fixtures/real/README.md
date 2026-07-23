# Fixed real-repository fixtures

These files are deliberately reduced to the smallest type-attributed source
that exercises the official recipe. They retain the original package, imports
and constructor/call shape; unrelated application code was removed so tests do
not need the repositories' complete dependency graphs.

| Fixture | Fixed source | License | Covered shape |
|---|---|---|---|
| `testcontainers-elasticsearch-container-test.java` | [`testcontainers/testcontainers-java@4a2ca136cf10e257336fd5621b20c444ed430df2`](https://github.com/testcontainers/testcontainers-java/blob/4a2ca136cf10e257336fd5621b20c444ed430df2/modules/elasticsearch/src/test/java/org/testcontainers/elasticsearch/ElasticsearchContainerTest.java) | MIT | no-arg constructor, chained `withEnv`, try-with-resources |
| `elastic-apm-real-reporter.java` | [`elastic/apm-agent-java@08ac41b483e5fa692b025ed7631a137078341803`](https://github.com/elastic/apm-agent-java/blob/08ac41b483e5fa692b025ed7631a137078341803/apm-agent-plugins/apm-es-restclient-plugin/apm-es-restclient-plugin-6_4/src/test/java/co/elastic/apm/agent/esrestclient/v6_4/ElasticsearchRestClientInstrumentationIT_RealReporter.java) | Apache-2.0 | field-owned container initialized by a no-arg constructor |

Both fixtures are loaded from resources and passed through the actual
`MakeElasticsearchContainerImageExplicit` runtime recipe in tests.
