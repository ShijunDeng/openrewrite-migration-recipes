package com.huawei.clouds.openrewrite.elasticsearch;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Mark source-level runtime behavior boundaries introduced between Testcontainers 1.17.6 and 1.21.4. */
public final class FindElasticsearchContainerSourceRisks extends Recipe {
    private static final String CONTAINER = "org.testcontainers.elasticsearch.ElasticsearchContainer";
    static final String OPERATIONAL_DEFAULTS =
            "Testcontainers Elasticsearch 1.21.4 不再自动添加随机 network alias，并新增 " +
            "cluster.routing.allocation.disk.threshold_enabled=false；请验证容器寻址、并行测试和磁盘水位场景";
    static final String NETWORK_ALIAS =
            "该代码依赖容器 network alias/网络寻址；1.21.4 删除了 ElasticsearchContainer 自动随机 alias，" +
            "请显式设置稳定 alias 并验证跨容器连接";
    static final String DISK_THRESHOLD =
            "1.21.4 构造器会默认设置 cluster.routing.allocation.disk.threshold_enabled=false；" +
            "请验证自定义 withEnv 的覆盖顺序和磁盘水位测试预期";
    static final String OSS_IMAGE =
            "Elasticsearch OSS 镜像在 7.10.2 后不再受支持，1.21.4 会发出弃用警告；" +
            "请迁移到默认发行版镜像并验证许可证与安全配置";
    static final String CERTIFICATE =
            "1.21.4 改为调用 caCertAsBytes() 时才复制 CA，createSslContextFromCa() 的缺失证书异常链也已变化；" +
            "请验证容器启动时序、证书路径、Optional/异常断言和重复读取";
    private static final Set<String> NETWORK_METHODS =
            Set.of("getNetworkAliases", "getNetwork", "getHost", "getContainerInfo");
    private static final Set<String> CERTIFICATE_METHODS =
            Set.of("caCertAsBytes", "createSslContextFromCa", "withCertPath");

    @Override
    public String getDisplayName() {
        return "Find Testcontainers Elasticsearch 1.21.4 source risks";
    }

    @Override
    public String getDescription() {
        return "Mark network alias, disk threshold, OSS image and lazy CA certificate behavior boundaries.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                String source = cu.printAll();
                boolean testcontainersType = source.contains(
                        "org.testcontainers.elasticsearch.ElasticsearchContainer") ||
                        source.contains("package org.testcontainers.elasticsearch;");
                if (ElasticsearchUpgradeSupport.generated(cu.getSourcePath()) ||
                    cu.getMarkers().findFirst(ElasticsearchProjectMarker.class).isEmpty() ||
                    !testcontainersType) {
                    return cu;
                }
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                return TypeUtils.isOfClassType(visited.getType(), CONTAINER)
                        ? mark(visited, OPERATIONAL_DEFAULTS) : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(method, ctx);
                if (!onContainer(visited)) return visited;
                if (NETWORK_METHODS.contains(visited.getSimpleName())) return mark(visited, NETWORK_ALIAS);
                if (CERTIFICATE_METHODS.contains(visited.getSimpleName())) return mark(visited, CERTIFICATE);
                if ("withEnv".equals(visited.getSimpleName()) && firstStringArgument(visited)
                        .equals("cluster.routing.allocation.disk.threshold_enabled")) {
                    return mark(visited, DISK_THRESHOLD);
                }
                return visited;
            }

            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal visited = super.visitLiteral(literal, ctx);
                return visited.getValue() instanceof String value && value.contains("elasticsearch-oss")
                        ? mark(visited, OSS_IMAGE) : visited;
            }
        };
    }

    private static boolean onContainer(J.MethodInvocation method) {
        Expression select = method.getSelect();
        if (select != null && TypeUtils.isAssignableTo(CONTAINER, select.getType())) return true;
        return method.getMethodType() != null &&
               TypeUtils.isAssignableTo(CONTAINER, method.getMethodType().getDeclaringType());
    }

    private static String firstStringArgument(J.MethodInvocation method) {
        if (method.getArguments().isEmpty()) return "";
        J argument = method.getArguments().get(0);
        return argument instanceof J.Literal literal && literal.getValue() instanceof String value ? value : "";
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))
                ? tree : SearchResult.found(tree, message);
    }
}
