package com.huawei.clouds.openrewrite.fastjson;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Map;
import java.util.Set;

/** Marks Fastjson 2 behavior choices that cannot be inferred safely from syntax. */
public final class FindFastjson2MigrationRisks extends Recipe {
    private static final String PARSER_CONFIG = "com.alibaba.fastjson.parser.ParserConfig";
    private static final String SERIALIZE_CONFIG = "com.alibaba.fastjson.serializer.SerializeConfig";
    private static final String SERIALIZER_FEATURE = "com.alibaba.fastjson.serializer.SerializerFeature";
    private static final String PARSER_FEATURE = "com.alibaba.fastjson.parser.Feature";
    private static final Map<String, String> SERIALIZER_FEATURE_MESSAGES = Map.of(
            "DisableCircularReferenceDetect", "Fastjson2 disables reference detection by default; native JSONWriter.Feature.ReferenceDetection has the opposite meaning",
            "UseISO8601DateFormat", "Fastjson2 has no equivalent writer feature; choose an explicit format such as iso8601",
            "WriteDateUseDateFormat", "Fastjson2 date defaults differ; choose an explicit format (millis if old millisecond output is required)",
            "SortField", "Fastjson2 JSONObject preserves insertion order; verify whether sorted output is an external protocol requirement",
            "MapSortField", "Fastjson2 map ordering differs; verify whether sorted output is an external protocol requirement"
    );
    private static final Map<String, String> PARSER_FEATURE_MESSAGES = Map.of(
            "SupportAutoType", "Do not mechanically enable Fastjson2 SupportAutoType; choose a narrow AutoTypeBeforeHandler allow-list",
            "IgnoreAutoType", "Fastjson2 removed the old AutoType whitelist model; verify the intended deny-by-default policy",
            "DisableFieldSmartMatch", "Fastjson2 smart matching is disabled by default; review whether SupportSmartMatch is actually required",
            "SafeMode", "Fastjson2 safe mode is a process-wide startup choice; verify deployment configuration instead of copying the old flag"
    );
    private static final Set<String> ANNOTATION_OPTIONS = Set.of(
            "serializeUsing", "deserializeUsing", "serialzeFeatures", "serializeFeatures", "parseFeatures", "format"
    );

    @Override
    public String getDisplayName() {
        return "Find behavior-sensitive Fastjson 2 migrations";
    }

    @Override
    public String getDescription() {
        return "Mark AutoType, global providers, custom serializers, feature defaults, date/reference behavior, and annotations that require application intent.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                if (!UpgradeSelectedFastjsonDependency.isProjectPath(compilationUnit.getSourcePath())) {
                    return compilationUnit;
                }
                return super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
                if (methodOn(m, PARSER_CONFIG, "getDeserializers")) {
                    return SearchResult.found(m, "The mutable deserializer map is absent from Fastjson 2 compatibility mode; use putDeserializer/getDeserializer when return semantics are equivalent, or redesign code that exposes or consumes the map");
                }
                if (methodOn(m, PARSER_CONFIG, "setAutoTypeSupport")) {
                    return SearchResult.found(m, "Fastjson2 removed the old AutoType whitelist model; use a narrow AutoTypeBeforeHandler and security tests");
                }
                if (methodOn(m, PARSER_CONFIG, "addAccept") || methodOn(m, PARSER_CONFIG, "addDeny") ||
                    methodOn(m, PARSER_CONFIG, "addDenyInternal")) {
                    return SearchResult.found(m, "Migrate this rule to JSONFactory.getDefaultObjectReaderProvider() and review its security boundary");
                }
                if (methodOn(m, PARSER_CONFIG, "setSafeMode")) {
                    return SearchResult.found(m, "Fastjson2 safe mode is process-wide and cannot be toggled incompatibly at runtime");
                }
                if (methodOn(m, PARSER_CONFIG, "getGlobalInstance") || methodOn(m, SERIALIZE_CONFIG, "getGlobalInstance")) {
                    return SearchResult.found(m, "Global reader/writer provider mutation affects the whole process; review initialization order and isolation");
                }
                if (methodOn(m, "com.alibaba.fastjson.JSON", "toJSONStringWithDateFormat")) {
                    return SearchResult.found(m, "Date and time-zone defaults changed; verify the explicit format against production payloads");
                }
                if (methodOn(m, "com.alibaba.fastjson.JSON", "handleResovleTask")) {
                    return SearchResult.found(m, "This parser-internal resolve API has no direct Fastjson2 replacement; redesign the custom parser integration");
                }
                return m;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                J.FieldAccess f = super.visitFieldAccess(fieldAccess, ctx);
                String owner = owner(f.getName().getFieldType());
                String message = featureMessage(owner, f.getSimpleName());
                if (message != null) {
                    return SearchResult.found(f, message);
                }
                if ("com.alibaba.fastjson.JSON".equals(owner) &&
                    ("DEFAULT_PARSER_FEATURE".equals(f.getSimpleName()) || "DEFAULT_GENERATE_FEATURE".equals(f.getSimpleName()))) {
                    return SearchResult.found(f, "Fastjson2 feature masks and defaults differ; replace this mutable global mask with an explicit reader/writer context");
                }
                return f;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                J.Identifier i = super.visitIdentifier(identifier, ctx);
                if (isFieldAccessName(getCursor())) {
                    return i;
                }
                String owner = owner(i.getFieldType());
                String message = featureMessage(owner, i.getSimpleName());
                return message == null ? i : SearchResult.found(i, message);
            }

            @Override
            public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                J.Annotation a = super.visitAnnotation(annotation, ctx);
                if (!TypeUtils.isOfClassType(a.getType(), "com.alibaba.fastjson.annotation.JSONField") &&
                    !TypeUtils.isOfClassType(a.getType(), "com.alibaba.fastjson.annotation.JSONType")) {
                    return a;
                }
                boolean risky = a.getArguments() != null && a.getArguments().stream().anyMatch(argument ->
                        argument instanceof J.Assignment assignment &&
                        assignment.getVariable() instanceof J.Identifier name &&
                        ANNOTATION_OPTIONS.contains(name.getSimpleName()));
                return risky ? SearchResult.found(a, "Fastjson2 annotation extension hooks and formatting defaults differ; verify this option explicitly") : a;
            }
        };
    }

    private static boolean methodOn(J.MethodInvocation method, String owner, String name) {
        return name.equals(method.getSimpleName()) && method.getMethodType() != null &&
               TypeUtils.isAssignableTo(owner, method.getMethodType().getDeclaringType());
    }

    private static String owner(JavaType.Variable fieldType) {
        return fieldType == null || fieldType.getOwner() == null ? null : fieldType.getOwner().toString();
    }

    private static String featureMessage(String owner, String name) {
        if (SERIALIZER_FEATURE.equals(owner)) {
            return SERIALIZER_FEATURE_MESSAGES.get(name);
        }
        if (PARSER_FEATURE.equals(owner)) {
            return PARSER_FEATURE_MESSAGES.get(name);
        }
        return null;
    }

    private static boolean isFieldAccessName(Cursor cursor) {
        Cursor parent = cursor.getParentTreeCursor();
        return parent.getValue() instanceof J.FieldAccess access && access.getName() == cursor.getValue();
    }
}
