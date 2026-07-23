# Real-repository fixtures

These fixtures are deliberately pinned to immutable commits. They are kept small
so each test exercises one migration boundary without importing an application.

| Fixture | Upstream source | License | Purpose |
|---|---|---|---|
| `service-registry-httptrace.java` | [`gla-rad/ServiceRegistry@82cc3faa`](https://github.com/gla-rad/ServiceRegistry/blob/82cc3faa6a576d572f28e27380f3fe375f0a9373/src/main/java/net/maritimeconnectivity/serviceregistry/config/GlobalConfig.java) | [Apache-2.0](https://github.com/gla-rad/ServiceRegistry/blob/82cc3faa6a576d572f28e27380f3fe375f0a9373/LICENSE) | Reduced source retaining the legacy `HttpTraceRepository` bean and its enabling property. |
| `veilarbportefolje-application-local.properties` | [`navikt/veilarbportefolje@1ac71826`](https://github.com/navikt/veilarbportefolje/blob/1ac718267c890ed878484d136556a0289db1a46a/src/main/resources/application-local.properties) | [MIT](https://github.com/navikt/veilarbportefolje/blob/1ac718267c890ed878484d136556a0289db1a46a/LICENSE.md) | Relevant leading configuration lines, excluding the unrelated credential-shaped sample value later in the upstream file. |

The fixtures are test data, not claims that the upstream projects currently
need this recipe. Their pinned historical states are used to make the regression
suite reproducible.
