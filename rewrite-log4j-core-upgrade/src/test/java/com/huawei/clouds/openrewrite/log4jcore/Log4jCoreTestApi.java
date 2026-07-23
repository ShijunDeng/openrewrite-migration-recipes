package com.huawei.clouds.openrewrite.log4jcore;

final class Log4jCoreTestApi {
    private Log4jCoreTestApi() {
    }

    static String[] legacySources() {
        return new String[]{
                "package org.apache.logging.log4j.core; public interface Filter {}",
                """
                package org.apache.logging.log4j.core.layout;
                public final class PatternLayout {
                    public static Builder newBuilder(){return null;}
                    public static class Builder {
                        public Builder withPattern(String pattern){return this;}
                        public Builder setPattern(String pattern){return this;}
                    }
                }
                """,
                """
                package org.apache.logging.log4j.core.config;
                import org.apache.logging.log4j.core.Filter;
                public class LoggerConfig {
                    public static Builder<?> newBuilder(){return null;}
                    public static class Builder<B extends Builder<B>> {
                        public B withtFilter(Filter f){return null;} public B withFilter(Filter f){return null;}
                        public B setFilter(Filter f){return null;}
                    }
                    public static class RootLogger {
                        public static class Builder<B extends Builder<B>> {
                            public B withtFilter(Filter f){return null;} public B setFilter(Filter f){return null;}
                        }
                    }
                }
                """,
                "package org.apache.logging.log4j.core.config.plugins; public @interface Plugin { String name(); String category(); String elementType() default \"\"; }",
                "package org.apache.logging.log4j.core.config.plugins.util; public final class PluginManager { public static void addPackage(String p){} public static void addPackages(java.util.Collection<String> p){} }",
                "package org.apache.logging.log4j.core.jmx; public final class Server { public static void reregisterMBeansAfterReconfigure(){} }",
                "package org.apache.logging.log4j.core.util.datetime; public class FixedDateFormat { public FixedDateFormat(){} public String format(long v){return null;} }",
                "package org.apache.logging.log4j.core.util.datetime; public class FastDateFormat { public FastDateFormat(){} public String format(long v){return null;} }",
                "package org.apache.logging.log4j.core.lookup; public class JndiLookup { public JndiLookup(){} public String lookup(Object e,String k){return null;} }",
                "package org.apache.logging.log4j.core.net; public class JndiManager { public JndiManager(){} public Object lookup(String k){return null;} }",
                "package org.apache.logging.log4j.core.appender.db.jdbc; public class JndiConnectionSource { public JndiConnectionSource(){} }",
                "package org.apache.logging.log4j.core.appender.mom; public class JmsAppender { public JmsAppender(){} }",
                "package org.apache.logging.log4j.core.script; public class Script { public Script(){} }",
                "package org.apache.logging.log4j.core.script; public class ScriptFile { public ScriptFile(){} }",
                "package org.apache.logging.log4j.core.script; public class ScriptRef { public ScriptRef(){} }",
                "package org.apache.logging.log4j.core.impl; public class ThrowableProxy { public ThrowableProxy(Throwable t){} }",
                "package org.apache.logging.log4j.core.impl; public class Log4jLogEvent { public static java.io.Serializable serialize(Log4jLogEvent e, boolean l){return null;} public static Log4jLogEvent deserialize(java.io.Serializable e){return null;} }"
        };
    }

    static String[] targetSources() {
        return new String[]{
                "package org.apache.logging.log4j.core; public interface Filter {}",
                """
                package org.apache.logging.log4j.core.config;
                import org.apache.logging.log4j.core.Filter;
                public class LoggerConfig {
                    public static Builder<?> newBuilder(){return null;}
                    public static class Builder<B extends Builder<B>> { public B setFilter(Filter f){return null;} }
                    public static class RootLogger { public static class Builder<B extends Builder<B>> { public B setFilter(Filter f){return null;} } }
                }
                """
        };
    }
}
