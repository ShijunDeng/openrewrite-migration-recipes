# Real-repository fixtures

These are deliberately reduced, compileable excerpts. They preserve the relevant
Jetty API shape while removing assertions and unrelated test infrastructure.

- `jetty9-http-fields.java` comes from
  `jetty/jetty.project@8f1440587e9e4ae7db3d74cf205643f3d707148d`,
  `jetty-http/src/test/java/org/eclipse/jetty/http/HttpFieldsTest.java`.
- `jetty9-http-uri.java` comes from the same fixed commit,
  `jetty-http/src/test/java/org/eclipse/jetty/http/HttpURITest.java`.
- `dropwizard-jetty12.java` comes from
  `dropwizard/dropwizard@6660674f7a81543a8a29c50d69f228fb382caaa6`,
  `dropwizard-request-logging/src/test/java/io/dropwizard/request/logging/LogbackAccessRequestLogTest.java`.

Jetty is dual-licensed under EPL-2.0 or Apache-2.0. Dropwizard is Apache-2.0.
The excerpts are used only as migration-test inputs and retain provenance here.
Besides direct risk-recipe tests, the fixtures are run together through the
recommended entry under a selected Jetty 9.4 build root and an unrelated target
build root to prove that the nearest-project gate prevents cross-project leakage.
