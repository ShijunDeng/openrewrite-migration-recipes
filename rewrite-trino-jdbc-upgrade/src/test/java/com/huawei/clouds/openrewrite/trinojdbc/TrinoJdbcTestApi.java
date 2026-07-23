package com.huawei.clouds.openrewrite.trinojdbc;

final class TrinoJdbcTestApi {
    private TrinoJdbcTestApi() {
    }

    static String[] sources() {
        return new String[]{
                """
                package io.trino.jdbc;
                public class TrinoConnection {
                    public Boolean isUseLegacyPreparedStatements() { return true; }
                    public boolean useExplicitPrepare() { return true; }
                    public void setSessionProperty(String name, String value) { }
                    public void setTimeZoneId(String id) { }
                    public void setLocale(java.util.Locale locale) { }
                    public void setCatalog(String catalog) { }
                    public void setSchema(String schema) { }
                    public void setClientInfo(String name, String value) { }
                    public void abort(java.util.concurrent.Executor executor) { }
                    public void setNetworkTimeout(java.util.concurrent.Executor executor, int millis) { }
                    public java.sql.PreparedStatement prepareStatement(String sql) { return null; }
                }
                """,
                """
                package io.trino.jdbc;
                public final class TrinoDriverUri {
                    public static TrinoDriverUri create(String url, java.util.Properties properties) { return null; }
                    public static boolean acceptsURL(String url) { return false; }
                }
                """,
                """
                package io.trino.jdbc;
                public class TrinoStatement {
                    public void setProgressMonitor(java.util.function.Consumer<QueryStats> monitor) { }
                    public void partialCancel() { }
                    public void cancel() { }
                    public void close() { }
                }
                """,
                """
                package io.trino.jdbc;
                public final class QueryStats {
                    public String getQueryId() { return null; }
                    public long getProcessedBytes() { return 0; }
                    public long getWallTimeMillis() { return 0; }
                }
                """,
                "package io.trino.jdbc.$internal; public class Secret { }"
        };
    }
}
