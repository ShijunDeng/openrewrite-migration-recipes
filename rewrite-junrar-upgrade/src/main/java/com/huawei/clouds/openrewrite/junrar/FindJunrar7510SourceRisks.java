package com.huawei.clouds.openrewrite.junrar;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/** Mark exact Junrar extraction, entry-path, stream, archive-format and exception boundaries. */
public final class FindJunrar7510SourceRisks extends Recipe {
    private static final MethodMatcher JUNRAR_EXTRACT =
            new MethodMatcher("com.github.junrar.Junrar extract(..)");
    private static final MethodMatcher EXTRACT_FILE =
            new MethodMatcher("com.github.junrar.Archive extractFile(..)");
    private static final MethodMatcher GET_INPUT_STREAM =
            new MethodMatcher("com.github.junrar.Archive getInputStream(..)");
    private static final MethodMatcher FILE_NAME =
            new MethodMatcher("com.github.junrar.rarfile.FileHeader getFileName()");
    private static final MethodMatcher FILE_NAME_W =
            new MethodMatcher("com.github.junrar.rarfile.FileHeader getFileNameW()");
    private static final MethodMatcher FILE_NAME_STRING =
            new MethodMatcher("com.github.junrar.rarfile.FileHeader getFileNameString()");
    private static final MethodMatcher FILE_NAME_BYTES =
            new MethodMatcher("com.github.junrar.rarfile.FileHeader getFileNameByteArray()");
    private static final MethodMatcher STREAM_LENGTH =
            new MethodMatcher("com.github.junrar.volume.InputStreamVolume getLength()");

    static final String DESTINATION =
            "Junrar.extract/业务 extractArchive 会把不可信条目写入目标目录；7.5.8 与 7.5.10 " +
            "分别修复反斜杠和 sibling-prefix 目录逃逸。请在独占 staging 目录校验 canonical/real path、" +
            "拒绝绝对路径与 ..、禁止 symlink/junction 跳转、定义已存在文件策略、限制权限/配额，成功后原子发布；" +
            "任何异常都清理部分输出并验证回滚";
    static final String CUSTOM_EXTRACTION =
            "自定义 Archive/FileHeader 解包绕过 LocalFolderExtractor 的目标目录保护；" +
            "每个 entry path 都必须在创建父目录和打开输出流前做平台无关分隔符归一化、canonical containment、" +
            "symlink 逐段检查、覆盖/权限/磁盘配额控制，并对 TOCTOU、重复名、case-folding 和部分写入做测试";
    static final String ARCHIVE_FORMAT =
            "Archive 解析/读取在 7.5.6-7.5.10 修复缺失 EndArcHeader、SubHeader packed-data 游标、" +
            "solid RAR v20 负索引和 MAC subblock；用真实 RAR2/3、solid、split volume、损坏/截断/加密、" +
            "空归档与恶意 corpus 验证接受/拒绝、输出 hash、资源上限和回滚";
    static final String STREAM =
            "InputStreamVolume.getLength 从固定 Long.MAX_VALUE 改为 InputStream.available()（IOException 时回退）；" +
            "available 不是总长度。请验证网络/分块/零 available/慢流、多卷进度、截断、阻塞和关闭语义";
    static final String EXCEPTION =
            "Junrar/RarException 异常边界在安全提取中必须 fail closed：区分格式、CRC、加密、I/O、" +
            "路径拒绝和资源耗尽，不把攻击输入静默降级为成功；记录安全事件并删除 staging 输出，验证重试与回滚";

    @Override
    public String getDisplayName() {
        return "Find Junrar 7.5.10 source migration risks";
    }

    @Override
    public String getDescription() {
        return "Mark exact extraction destinations, custom entry-path handling, archive/stream behavior and " +
               "RarException rollback boundaries that have no behavior-preserving automatic rewrite.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof J.CompilationUnit cu) ||
                    JunrarSupport.generated(cu.getSourcePath())) return tree;
                return new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(
                            J.MethodDeclaration method, ExecutionContext ec) {
                        J.MethodDeclaration visited = super.visitMethodDeclaration(method, ec);
                        if ("extractArchive".equals(visited.getSimpleName()) &&
                            (!FindMethods.find(visited,
                                    "com.github.junrar.Junrar extract(..)").isEmpty() ||
                             !FindMethods.find(visited,
                                    "com.github.junrar.Archive extractFile(..)").isEmpty())) {
                            return mark(visited, DESTINATION);
                        }
                        return visited;
                    }

                    @Override
                    public J.MethodInvocation visitMethodInvocation(
                            J.MethodInvocation method, ExecutionContext ec) {
                        J.MethodInvocation visited = super.visitMethodInvocation(method, ec);
                        if (JUNRAR_EXTRACT.matches(visited)) return mark(visited, DESTINATION);
                        if (EXTRACT_FILE.matches(visited) || FILE_NAME.matches(visited) ||
                            FILE_NAME_W.matches(visited) || FILE_NAME_STRING.matches(visited) ||
                            FILE_NAME_BYTES.matches(visited)) return mark(visited, CUSTOM_EXTRACTION);
                        if (GET_INPUT_STREAM.matches(visited)) return mark(visited, ARCHIVE_FORMAT);
                        if (STREAM_LENGTH.matches(visited)) return mark(visited, STREAM);
                        return visited;
                    }

                    @Override
                    public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ec) {
                        J.NewClass visited = super.visitNewClass(newClass, ec);
                        return TypeUtils.isOfClassType(visited.getType(), "com.github.junrar.Archive") ?
                                mark(visited, ARCHIVE_FORMAT) : visited;
                    }

                    @Override
                    public J.Try.Catch visitCatch(J.Try.Catch catchable, ExecutionContext ec) {
                        J.Try.Catch visited = super.visitCatch(catchable, ec);
                        return TypeUtils.isAssignableTo(
                                "com.github.junrar.exception.RarException",
                                visited.getParameter().getTree().getType()) ?
                                mark(visited, EXCEPTION) : visited;
                    }
                }.visitNonNull(cu, ctx);
            }
        };
    }

    private static <T extends Tree> T mark(T tree, String message) {
        if (tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription()))) return tree;
        return SearchResult.found(tree, message);
    }
}
