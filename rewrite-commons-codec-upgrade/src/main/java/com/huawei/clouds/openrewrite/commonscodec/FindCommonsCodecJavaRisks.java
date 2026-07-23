package com.huawei.clouds.openrewrite.commonscodec;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.util.Set;

/** Locate algorithm and exception-policy decisions that cannot be inferred safely from syntax alone. */
public final class FindCommonsCodecJavaRisks extends Recipe {
    static final String MURMUR =
            "Legacy MurmurHash3 entry points intentionally preserve sign-extension/default-charset bugs; choose the " +
            "corrected x86/x64 API only with a hash-compatibility and persisted-key migration plan";
    static final String PHONETIC =
            "Commons Codec 1.13-1.22 fixes phonetic output for Cologne, Metaphone, DoubleMetaphone and related encoders; " +
            "rebuild golden data, indexes and match thresholds before accepting changed codes";
    static final String STRICT =
            "Strict Base16/Base32/Base64 decoding rejects non-zero trailing bits and malformed input that lenient mode " +
            "discarded; verify protocol policy, error mapping, padding, custom alphabets and streaming boundaries";
    static final String ARRAY_OWNERSHIP =
            "Base32/Base64 constructors now defensively copy separators and alphabets; code that mutates the source " +
            "array after construction changes behavior, so make ownership explicit or migrate to the builder";
    static final String MD5_PREFIX =
            "Md5Crypt rejects invalid prefixes with IllegalArgumentException since 1.17.1; validate untrusted salt " +
            "prefixes and preserve the application's authentication/error policy";
    static final String RFC1522 =
            "BCodec/QCodec encode can surface UnsupportedCharsetException instead of EncoderException; verify catches, " +
            "HTTP/mail error mapping and whether the requested charset is controlled";
    static final String HEX_BUFFER =
            "Hex ByteBuffer handling was corrected to honor position/remaining/read-only buffers; verify callers that " +
            "reuse slices or depend on backing-array capacity and position changes";
    static final String CHARSET =
            "A static import still owns a deprecated org.apache.commons.codec.Charsets constant; replace it with the " +
            "matching java.nio.charset.StandardCharsets field after raising the Java baseline";

    private static final Set<String> PHONETIC_TYPES = Set.of(
            "org.apache.commons.codec.language.ColognePhonetic",
            "org.apache.commons.codec.language.Metaphone",
            "org.apache.commons.codec.language.DoubleMetaphone",
            "org.apache.commons.codec.language.DaitchMokotoffSoundex",
            "org.apache.commons.codec.language.MatchRatingApproachEncoder",
            "org.apache.commons.codec.language.RefinedSoundex");
    private static final Set<String> LEGACY_MURMUR = Set.of("hash32", "hash64", "hash128");

    @Override
    public String getDisplayName() {
        return "Find Apache Commons Codec 1.22 Java migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks hash/output compatibility, phonetic bug fixes, strict decoding, mutable constructor arrays, " +
               "crypt prefixes, RFC 1522 exceptions, ByteBuffer semantics, and static charset imports.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit compilationUnit, ExecutionContext ctx) {
                return UpgradeSelectedCommonsCodecDependency.generated(compilationUnit.getSourcePath())
                        ? compilationUnit : super.visitCompilationUnit(compilationUnit, ctx);
            }

            @Override
            public J.Import visitImport(J.Import anImport, ExecutionContext ctx) {
                J.Import visited = super.visitImport(anImport, ctx);
                return visited.isStatic() && visited.printTrimmed().contains("org.apache.commons.codec.Charsets.")
                        ? mark(visited, CHARSET) : visited;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                JavaType.Method method = visited.getMethodType();
                if (method == null) return visited;
                String owner = owner(method.getDeclaringType());
                String name = method.getName();
                if ("org.apache.commons.codec.digest.MurmurHash3".equals(owner) && LEGACY_MURMUR.contains(name)) {
                    return mark(visited, MURMUR);
                }
                if (owner.endsWith("MurmurHash3$IncrementalHash32")) return mark(visited, MURMUR);
                if (PHONETIC_TYPES.contains(owner) &&
                    Set.of("encode", "colognePhonetic", "metaphone", "doubleMetaphone", "soundex").contains(name)) {
                    return mark(visited, PHONETIC);
                }
                if ((owner.startsWith("org.apache.commons.codec.binary.Base") ||
                     owner.startsWith("org.apache.commons.codec.net.BCodec") ||
                     owner.startsWith("org.apache.commons.codec.net.QCodec")) &&
                    Set.of("setDecodingPolicy", "setCodecPolicy").contains(name) &&
                    visited.getArguments().stream().anyMatch(FindCommonsCodecJavaRisks::strictPolicy)) {
                    return mark(visited, STRICT);
                }
                if (owner.startsWith("org.apache.commons.codec.binary.Base16") && "decode".equals(name)) {
                    return mark(visited, STRICT);
                }
                if ("org.apache.commons.codec.digest.Md5Crypt".equals(owner) &&
                    Set.of("md5Crypt", "apr1Crypt").contains(name)) return mark(visited, MD5_PREFIX);
                if (("org.apache.commons.codec.net.BCodec".equals(owner) ||
                     "org.apache.commons.codec.net.QCodec".equals(owner)) && "encode".equals(name)) {
                    return mark(visited, RFC1522);
                }
                if ("org.apache.commons.codec.binary.Hex".equals(owner) && hasByteBufferArgument(visited)) {
                    return mark(visited, HEX_BUFFER);
                }
                return visited;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                J.NewClass visited = super.visitNewClass(newClass, ctx);
                String type = owner(visited.getType());
                if (type.endsWith("MurmurHash3$IncrementalHash32")) return mark(visited, MURMUR);
                if ((type.startsWith("org.apache.commons.codec.binary.Base32") ||
                     type.startsWith("org.apache.commons.codec.binary.Base64")) &&
                    visited.getArguments().stream().anyMatch(FindCommonsCodecJavaRisks::strictPolicy)) return mark(visited, STRICT);
                if (("org.apache.commons.codec.binary.Base32".equals(type) ||
                     "org.apache.commons.codec.binary.Base64".equals(type)) &&
                    visited.getArguments().stream().anyMatch(FindCommonsCodecJavaRisks::isByteArray)) {
                    return mark(visited, ARRAY_OWNERSHIP);
                }
                return visited;
            }
        };
    }

    private static boolean hasByteBufferArgument(J.MethodInvocation invocation) {
        return invocation.getArguments().stream().map(Expression::getType)
                .anyMatch(type -> TypeUtils.isOfClassType(type, "java.nio.ByteBuffer"));
    }

    private static boolean isByteArray(Expression expression) {
        JavaType type = expression.getType();
        return type instanceof JavaType.Array array && array.getElemType() == JavaType.Primitive.Byte;
    }

    private static boolean strictPolicy(Expression expression) {
        if (!TypeUtils.isOfClassType(expression.getType(), "org.apache.commons.codec.CodecPolicy")) return false;
        if (expression instanceof J.FieldAccess field) return "STRICT".equals(field.getSimpleName());
        return expression instanceof J.Identifier identifier && "STRICT".equals(identifier.getSimpleName());
    }

    private static String owner(JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? "" : fullyQualified.getFullyQualifiedName();
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree : SearchResult.found(tree, message);
    }
}
