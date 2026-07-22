package com.huawei.clouds.openrewrite.reactresizable;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.JavaScriptIsoVisitor;
import org.openrewrite.javascript.JavaScriptParser;
import org.openrewrite.javascript.tree.JS;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Attach the v3-required handle ref only in a narrow, provably local inline native-element form. */
public final class MigrateDeterministicReactResizableHandles extends Recipe {
    private static final String IDENTIFIER = "[A-Za-z_$][A-Za-z0-9_$]*";
    private static final Pattern REF_ATTRIBUTE = Pattern.compile("(?:^|\\s)ref\\s*=");
    private static final Pattern SPREAD_ATTRIBUTE = Pattern.compile("\\{\\s*\\.\\.\\.");

    @Override
    public String getDisplayName() {
        return "Attach refs to deterministic react-resizable custom handles";
    }

    @Override
    public String getDescription() {
        return "For imported Resizable/ResizableBox components, add the v3 handle callback ref parameter and " +
               "attach it to an exact self-closing intrinsic JSX element; leave components, blocks, typed/dynamic " +
               "callbacks, spreads, existing refs, and shadowed bindings for review.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaScriptIsoVisitor<ExecutionContext>() {
            @Override
            public JS.CompilationUnit visitJsCompilationUnit(JS.CompilationUnit cu, ExecutionContext ctx) {
                if (!ReactResizableSupport.isProjectPath(cu.getSourcePath())) return cu;
                Set<String> aliases = new HashSet<>();
                Map<String, Integer> declarations = new HashMap<>();
                new JavaScriptIsoVisitor<ExecutionContext>() {
                    @Override
                    public JS.Import visitImportDeclaration(JS.Import declaration, ExecutionContext scanCtx) {
                        JS.Import visited = super.visitImportDeclaration(declaration, scanCtx);
                        if (ReactResizableSupport.PACKAGE.equals(ReactResizableSupport.moduleName(visited)) &&
                            !visited.printTrimmed(getCursor()).startsWith("import type ")) {
                            for (String api : List.of("Resizable", "ResizableBox")) {
                                String alias = ReactResizableSupport.importedAlias(visited, api);
                                if (alias != null) aliases.add(alias);
                            }
                        }
                        return visited;
                    }

                    @Override
                    public J.VariableDeclarations.NamedVariable visitVariable(
                            J.VariableDeclarations.NamedVariable variable, ExecutionContext scanCtx) {
                        J.VariableDeclarations.NamedVariable visited = super.visitVariable(variable, scanCtx);
                        declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method,
                                                                      ExecutionContext scanCtx) {
                        J.MethodDeclaration visited = super.visitMethodDeclaration(method, scanCtx);
                        declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration,
                                                                    ExecutionContext scanCtx) {
                        J.ClassDeclaration visited = super.visitClassDeclaration(declaration, scanCtx);
                        declarations.merge(visited.getSimpleName(), 1, Integer::sum);
                        return visited;
                    }

                    @Override
                    public JS.TypeDeclaration visitTypeDeclaration(JS.TypeDeclaration declaration,
                                                                   ExecutionContext scanCtx) {
                        JS.TypeDeclaration visited = super.visitTypeDeclaration(declaration, scanCtx);
                        declarations.merge(visited.getName().getSimpleName(), 1, Integer::sum);
                        return visited;
                    }
                }.visit(cu, ctx);
                aliases.removeIf(alias -> declarations.getOrDefault(alias, 0) != 0);
                if (aliases.isEmpty()) return cu;

                String source = cu.printAll();
                String migrated = source;
                for (String alias : aliases) migrated = migrateAlias(migrated, alias);
                if (migrated.equals(source)) return cu;
                return reparse(cu, migrated, ctx);
            }
        };
    }

    private static String migrateAlias(String source, String alias) {
        Pattern callback = Pattern.compile(
                "(?s)(?<head><" + Pattern.quote(alias) + "\\b[^<>]*?\\bhandle\\s*=\\s*\\{\\s*)" +
                "(?:\\(\\s*(?<axis1>" + IDENTIFIER + ")\\s*\\)|(?<axis2>" + IDENTIFIER + "))" +
                "(?<arrow>\\s*=>\\s*)<(?<element>[a-z][A-Za-z0-9:_-]*)(?<attrs>[^<>]*?)" +
                "(?<close>\\s*/>\\s*})");
        boolean[] code = codePositions(source);
        Matcher matcher = callback.matcher(source);
        StringBuilder result = new StringBuilder(source.length());
        int last = 0;
        boolean changed = false;
        while (matcher.find()) {
            String attrs = matcher.group("attrs");
            if (!code[matcher.start()] || REF_ATTRIBUTE.matcher(attrs).find() ||
                SPREAD_ATTRIBUTE.matcher(attrs).find()) continue;
            String ref = refName(attrs);
            String axis = matcher.group("axis1") == null ? matcher.group("axis2") : matcher.group("axis1");
            result.append(source, last, matcher.start())
                    .append(matcher.group("head"))
                    .append('(').append(axis).append(", ").append(ref).append(')')
                    .append(matcher.group("arrow"))
                    .append('<').append(matcher.group("element")).append(" ref={").append(ref).append('}')
                    .append(attrs).append(matcher.group("close"));
            last = matcher.end();
            changed = true;
        }
        return changed ? result.append(source, last, source.length()).toString() : source;
    }

    private static String refName(String body) {
        for (String candidate : List.of("resizeHandleRef", "reactResizableRef", "handleElementRef")) {
            if (!Pattern.compile("(?<![A-Za-z0-9_$])" + candidate + "(?![A-Za-z0-9_$])")
                    .matcher(body).find()) return candidate;
        }
        return "reactResizableHandleElementRef";
    }

    private static JS.CompilationUnit reparse(JS.CompilationUnit original, String source, ExecutionContext ctx) {
        Path path = original.getSourcePath();
        Parser.Input input = Parser.Input.fromString(path, source, original.getCharset());
        JS.CompilationUnit parsed = (JS.CompilationUnit) JavaScriptParser.builder().build()
                .parseInputs(List.of(input), Path.of("."), ctx).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unable to parse migrated " + path));
        return parsed.withId(original.getId()).withSourcePath(path).withFileAttributes(original.getFileAttributes())
                .withCharsetBomMarked(original.isCharsetBomMarked()).withMarkers(original.getMarkers());
    }

    private static boolean[] codePositions(String source) {
        boolean[] code = new boolean[source.length() + 1];
        State state = State.CODE;
        boolean escaped = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            code[index] = state == State.CODE;
            if (state == State.LINE_COMMENT) {
                if (current == '\n' || current == '\r') state = State.CODE;
            } else if (state == State.BLOCK_COMMENT) {
                if (current == '*' && next == '/') {
                    code[index + 1] = false;
                    index++;
                    state = State.CODE;
                }
            } else if (state != State.CODE) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == state.terminator) state = State.CODE;
            } else if (current == '/' && next == '/') {
                code[index + 1] = false;
                index++;
                state = State.LINE_COMMENT;
            } else if (current == '/' && next == '*') {
                code[index + 1] = false;
                index++;
                state = State.BLOCK_COMMENT;
            } else if (current == '\'') state = State.SINGLE_QUOTE;
            else if (current == '"') state = State.DOUBLE_QUOTE;
            else if (current == '`') state = State.TEMPLATE;
        }
        code[source.length()] = state == State.CODE;
        return code;
    }

    private enum State {
        CODE('\0'), SINGLE_QUOTE('\''), DOUBLE_QUOTE('"'), TEMPLATE('`'),
        LINE_COMMENT('\0'), BLOCK_COMMENT('\0');
        private final char terminator;
        State(char terminator) { this.terminator = terminator; }
    }
}
