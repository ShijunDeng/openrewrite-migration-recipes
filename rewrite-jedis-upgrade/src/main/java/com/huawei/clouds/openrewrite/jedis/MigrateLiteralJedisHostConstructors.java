package com.huawei.clouds.openrewrite.jedis;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;
import java.util.regex.Pattern;

/** Makes the pre-v4 one-string host constructor unambiguous without guessing dynamic strings. */
public final class MigrateLiteralJedisHostConstructors extends Recipe {
    private static final Pattern SIMPLE_HOST = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    public String getDisplayName() {
        return "Make literal Jedis host constructors explicit";
    }

    @Override
    public String getDescription() {
        return "Add Protocol.DEFAULT_PORT to one-string Jedis and JedisPool constructors only when the argument is an unambiguous literal host name.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (n.getClazz() == null) {
                    return n;
                }
                String className = n.getClazz().printTrimmed(getCursor());
                if (!("Jedis".equals(className) || "redis.clients.jedis.Jedis".equals(className) ||
                      "JedisPool".equals(className) || "redis.clients.jedis.JedisPool".equals(className))) {
                    return n;
                }
                String targetType = className.endsWith("JedisPool") ?
                        "redis.clients.jedis.JedisPool" : "redis.clients.jedis.Jedis";
                if (!isOfficialJedisType(n, className, targetType)) {
                    return n;
                }
                List<Expression> arguments = n.getArguments();
                if (arguments.size() != 1 || !(arguments.get(0) instanceof J.Literal literal) ||
                    !(literal.getValue() instanceof String host) || !SIMPLE_HOST.matcher(host).matches()) {
                    return n;
                }
                String simpleName = className.endsWith("JedisPool") ? "JedisPool" : "Jedis";
                return JavaTemplate.builder("new " + simpleName +
                                "(#{any(String)}, redis.clients.jedis.Protocol.DEFAULT_PORT)")
                        .build()
                        .apply(updateCursor(n), n.getCoordinates().replace(), arguments.get(0));
            }

            private boolean isOfficialJedisType(J.NewClass n, String className, String targetType) {
                return targetType.equals(className) || TypeUtils.isOfClassType(n.getType(), targetType);
            }
        };
    }
}
