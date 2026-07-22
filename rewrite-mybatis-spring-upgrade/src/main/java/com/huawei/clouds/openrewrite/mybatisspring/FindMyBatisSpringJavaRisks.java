package com.huawei.clouds.openrewrite.mybatisspring;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.HashSet;
import java.util.Set;

/** Marks Java migration decisions that are unsafe to infer globally. */
public final class FindMyBatisSpringJavaRisks extends Recipe {
    private static final String MAPPER_SCAN = "org.mybatis.spring.annotation.MapperScan";
    private static final String SCANNER = "org.mybatis.spring.mapper.ClassPathMapperScanner";
    private static final String SCANNER_CONFIGURER = "org.mybatis.spring.mapper.MapperScannerConfigurer";
    private static final String SYSTEM_EXCEPTION = "org.mybatis.spring.MyBatisSystemException";
    private static final String BATCH_WRITER = "org.mybatis.spring.batch.MyBatisBatchItemWriter";
    private static final String ENABLE_BATCH_PROCESSING =
            "org.springframework.batch.core.configuration.annotation.EnableBatchProcessing";

    @Override
    public String getDisplayName() {
        return "Find MyBatis-Spring 4 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark mapper scan conflicts, deprecated scanner construction/setters, deprecated MyBatis exceptions, " +
               "old Spring Batch writer overrides, Batch infrastructure configuration, and Jakarta EE remnants.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final AnnotationMatcher mapperScan = new AnnotationMatcher("@" + MAPPER_SCAN);
            private final AnnotationMatcher enableBatch = new AnnotationMatcher("@" + ENABLE_BATCH_PROCESSING);

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (mapperScan.matches(a)) {
                    Set<String> attributes = annotationAttributes(a);
                    if (attributes.contains("value") && attributes.contains("basePackages")) {
                        return SearchResult.found(a,
                                "@MapperScan value and basePackages are @AliasFor aliases; keep only one after verifying packages");
                    }
                    if (attributes.contains("sqlSessionFactoryRef") && attributes.contains("sqlSessionTemplateRef")) {
                        return SearchResult.found(a,
                                "@MapperScan specifies both factory and template references; select one session boundary explicitly");
                    }
                }
                return enableBatch.matches(a)
                        ? SearchResult.found(a,
                        "Spring Batch 6 changes repository defaults and restart metadata; review data source, transaction manager, and failed executions")
                        : a;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass n = super.visitNewClass(newClass, ctx);
                if (TypeUtils.isOfClassType(n.getType(), SCANNER) && n.getArguments().size() == 1) {
                    return SearchResult.found(n,
                            "The one-argument ClassPathMapperScanner constructor is for removal; supply the matching Spring Environment explicitly");
                }
                if (TypeUtils.isOfClassType(n.getType(), SYSTEM_EXCEPTION) && n.getArguments().size() == 1) {
                    return SearchResult.found(n,
                            "The one-argument MyBatisSystemException constructor is for removal; preserve the intended message with the message/cause constructor");
                }
                return n;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (methodType == null) {
                    return m;
                }
                if (TypeUtils.isOfClassType(methodType.getDeclaringType(), SCANNER_CONFIGURER) &&
                    ("setSqlSessionFactory".equals(methodType.getName()) ||
                     "setSqlSessionTemplate".equals(methodType.getName()))) {
                    return SearchResult.found(m,
                            "MapperScannerConfigurer object injection is deprecated and can initialize too early; use the corresponding bean-name setter");
                }
                if (TypeUtils.isOfClassType(methodType.getDeclaringType(), SCANNER) &&
                    "setMapperFactoryBean".equals(methodType.getName())) {
                    return SearchResult.found(m,
                            "setMapperFactoryBean is for removal; migrate to setMapperFactoryBeanClass after reviewing instance state");
                }
                return m;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                JavaType.Method methodType = m.getMethodType();
                if (methodType != null && "write".equals(methodType.getName()) &&
                    TypeUtils.isAssignableTo(BATCH_WRITER, methodType.getDeclaringType()) &&
                    methodType.getParameterTypes().size() == 1 &&
                    TypeUtils.isOfClassType(methodType.getParameterTypes().get(0), "java.util.List")) {
                    return SearchResult.found(m,
                            "Spring Batch changed ItemWriter.write from List to Chunk; migrate this override and review chunk/error semantics");
                }
                return m;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier id = super.visitIdentifier(identifier, ctx);
                if (getCursor().firstEnclosing(J.Import.class) != null) {
                    return id;
                }
                JavaType.FullyQualified type = TypeUtils.asFullyQualified(id.getType());
                if (type == null || !id.getSimpleName().equals(type.getClassName())) {
                    return id;
                }
                return isJakartaEeType(type.getFullyQualifiedName())
                        ? SearchResult.found(id,
                        "Spring Framework 7 uses Jakarta APIs; migrate this javax EE type and dependency with the platform")
                        : id;
            }
        };
    }

    private static Set<String> annotationAttributes(J.Annotation annotation) {
        Set<String> names = new HashSet<>();
        if (annotation.getArguments() == null) {
            return names;
        }
        for (Expression argument : annotation.getArguments()) {
            if (argument instanceof J.Assignment assignment && assignment.getVariable() instanceof J.Identifier id) {
                names.add(id.getSimpleName());
            } else {
                names.add("value");
            }
        }
        return names;
    }

    private static boolean isJakartaEeType(String fqn) {
        return fqn.startsWith("javax.persistence.") || fqn.startsWith("javax.validation.") ||
               fqn.startsWith("javax.servlet.") ||
               fqn.startsWith("javax.transaction.") && !fqn.startsWith("javax.transaction.xa.");
    }
}
