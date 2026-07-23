package com.huawei.clouds.openrewrite.bson;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate BSON 5 migration decisions that require data-format or application evidence. */
public final class FindBson5SourceRisks extends Recipe {
    static final String UUID =
            "The no-argument UuidCodec default changed from JAVA_LEGACY to UNSPECIFIED, which disables encoding; " +
            "choose the stored UUID byte order/subtype explicitly and test old plus new UUID data";
    static final String JSON =
            "Document/BsonDocument toJson() uses relaxed Extended JSON in BSON 4+ instead of the 3.x strict default; " +
            "select JsonWriterSettings explicitly if logs, APIs, snapshots or downstream parsers depend on type wrappers";
    static final String OBJECT_ID_SERIALIZATION =
            "ObjectId Java serialization changed in BSON 4.2 and older payloads can throw InvalidClassException; version " +
            "or rebuild this payload and test rolling upgrades instead of assuming cross-version compatibility";
    static final String OBJECT_ID_COMPONENTS =
            "Legacy ObjectId machine/process/counter construction or access was removed; preserve raw 12-byte identity " +
            "only when required and redesign code that depends on host/process identifiers or global counters";
    static final String DECIMAL =
            "BSON 5 makes BsonDecimal128.isNumber() true and asNumber() return a BsonNumber; verify numeric dispatch, " +
            "visitor branches, coercion, equality and JSON/schema behavior for Decimal128 values";
    static final String REMOVED_CODECS =
            "Public MapCodec/IterableCodec were removed in BSON 5; register MapCodecProvider, CollectionCodecProvider or " +
            "IterableCodecProvider through a CodecRegistry and verify generic element/value plus UUID handling";
    static final String PARAMETERIZABLE =
            "The Parameterizable codec interface was removed in BSON 5; move parameterized-type selection into " +
            "CodecProvider.get(), retain TypeWithTypeParameters information and test nested generic containers";
    static final String READER_MARK =
            "BsonReader.mark()/reset() were removed; own the BsonReaderMark returned by getMark(), reset that exact mark, " +
            "and verify nesting, exception paths and reader state restoration";
    static final String WRITER_FLUSH =
            "BsonWriter.flush() was removed; decide whether the owned underlying stream/buffer must be flushed or closed " +
            "and verify partial writes, exceptions and resource ownership";
    static final String LEGACY_API =
            "This deprecated BSON hook, byte utility or org.bson.util API became inaccessible/removed after 3.12; replace " +
            "it with public Codec/BsonReader/BsonWriter/JDK APIs and verify byte order plus transformation semantics";

    private static final Set<String> JSON_TYPES = Set.of(
            "org.bson.Document", "org.bson.BsonDocument", "org.bson.RawBsonDocument");
    private static final Set<String> REMOVED_CODEC_TYPES = Set.of(
            "org.bson.codecs.MapCodec", "org.bson.codecs.IterableCodec");
    private static final Set<String> LEGACY_OBJECT_ID_METHODS = Set.of(
            "createFromLegacyFormat", "getCurrentCounter", "getGeneratedMachineIdentifier",
            "getGeneratedProcessIdentifier", "getMachineIdentifier", "getProcessIdentifier", "getCounter");

    @Override
    public String getDisplayName() {
        return "Find MongoDB BSON 5 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark UUID/JSON/data serialization changes, removed codecs and reader/writer contracts, Decimal128 dispatch, legacy ObjectId structure, and inaccessible 3.x utility APIs.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                return BsonSupport.generated(cu.getSourcePath()) ? cu : super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                String name = visited.getTypeName();
                if (REMOVED_CODEC_TYPES.contains(name)) return mark(visited, REMOVED_CODECS);
                if ("org.bson.codecs.Parameterizable".equals(name)) return mark(visited, PARAMETERIZABLE);
                if (legacyTypeName(name)) return mark(visited, LEGACY_API);
                return visited;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(classDecl, ctx);
                JavaType.FullyQualified type = visited.getType();
                if (type != null && TypeUtils.isAssignableTo("org.bson.codecs.Parameterizable", type)) {
                    return mark(visited, PARAMETERIZABLE);
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                JavaType type = visited.getType();
                if (TypeUtils.isOfClassType(type, "org.bson.codecs.UuidCodec") && noArguments(visited)) {
                    return mark(visited, UUID);
                }
                if (REMOVED_CODEC_TYPES.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name))) {
                    return mark(visited, REMOVED_CODECS);
                }
                if (TypeUtils.isOfClassType(type, "org.bson.types.ObjectId") && legacyObjectIdConstructor(visited)) {
                    return mark(visited, OBJECT_ID_COMPONENTS);
                }
                return visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                JavaType owner = method == null ? null : method.getDeclaringType();
                String name = visited.getSimpleName();
                if ("toJson".equals(name) && noArguments(visited) && isAny(owner, JSON_TYPES)) return mark(visited, JSON);
                if (Set.of("isNumber", "asNumber").contains(name) &&
                    TypeUtils.isOfClassType(owner, "org.bson.BsonDecimal128")) return mark(visited, DECIMAL);
                if (Set.of("mark", "reset").contains(name) &&
                    TypeUtils.isAssignableTo("org.bson.BsonReader", owner)) return mark(visited, READER_MARK);
                if ("flush".equals(name) && TypeUtils.isAssignableTo("org.bson.BsonWriter", owner)) {
                    return mark(visited, WRITER_FLUSH);
                }
                if (LEGACY_OBJECT_ID_METHODS.contains(name) &&
                    TypeUtils.isOfClassType(owner, "org.bson.types.ObjectId")) return mark(visited, OBJECT_ID_COMPONENTS);
                if (TypeUtils.isOfClassType(owner, "org.bson.BSON") || legacyType(owner)) return mark(visited, LEGACY_API);
                if ("writeObject".equals(name) && TypeUtils.isOfClassType(owner, "java.io.ObjectOutputStream") &&
                    !visited.getArguments().isEmpty() &&
                    TypeUtils.isOfClassType(visited.getArguments().get(0).getType(), "org.bson.types.ObjectId")) {
                    return mark(visited, OBJECT_ID_SERIALIZATION);
                }
                return visited;
            }

            @Override
            public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                J.TypeCast visited = super.visitTypeCast(typeCast, ctx);
                if (!TypeUtils.isOfClassType(visited.getClazz().getType(), "org.bson.types.ObjectId") ||
                    !(visited.getExpression() instanceof J.MethodInvocation read) || read.getMethodType() == null ||
                    !"readObject".equals(read.getSimpleName()) ||
                    !TypeUtils.isOfClassType(read.getMethodType().getDeclaringType(), "java.io.ObjectInputStream")) {
                    return visited;
                }
                return mark(visited, OBJECT_ID_SERIALIZATION);
            }
        };
    }

    private static boolean noArguments(J.MethodInvocation method) {
        return method.getArguments().isEmpty() || method.getArguments().stream().allMatch(J.Empty.class::isInstance);
    }

    private static boolean noArguments(J.NewClass constructor) {
        return constructor.getArguments().isEmpty() ||
               constructor.getArguments().stream().allMatch(J.Empty.class::isInstance);
    }

    private static boolean legacyObjectIdConstructor(J.NewClass objectId) {
        JavaType.Method constructor = objectId.getConstructorType();
        if (constructor == null) return false;
        int size = constructor.getParameterTypes().size();
        return size == 4 || size == 3 && constructor.getParameterTypes().stream()
                .allMatch(type -> type instanceof JavaType.Primitive);
    }

    private static boolean isAny(JavaType type, Set<String> names) {
        return names.stream().anyMatch(name -> TypeUtils.isOfClassType(type, name));
    }

    private static boolean legacyType(JavaType type) {
        JavaType.FullyQualified fq = TypeUtils.asFullyQualified(type);
        return fq != null && legacyTypeName(fq.getFullyQualifiedName());
    }

    private static boolean legacyTypeName(String name) {
        return "org.bson.BSON".equals(name) || "org.bson.io.Bits".equals(name) || name.startsWith("org.bson.util.");
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
