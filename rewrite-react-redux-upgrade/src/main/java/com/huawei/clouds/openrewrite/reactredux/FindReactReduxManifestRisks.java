package com.huawei.clouds.openrewrite.reactredux;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Marks only manifest declarations with a documented React Redux 9 compatibility decision. */
public final class FindReactReduxManifestRisks extends Recipe {
    private static final Pattern LOWER_BOUND = Pattern.compile(
            "^(?:\\^|~|>=|>)?\\s*(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?.*$");

    @Override
    public String getDisplayName() {
        return "Find React Redux 9 manifest migration risks";
    }

    @Override
    public String getDescription() {
        return "Marks unresolved React Redux declarations and central owners plus incompatible React 18, Redux 5, " +
               "Redux Toolkit 2, React type, ReactDOM, TypeScript, and obsolete external-type declarations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private Map<String, String> direct = Map.of();

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!ReactReduxSupport.isPackageJson(document.getSourcePath())) return document;
                Map<String, String> previous = direct;
                direct = directDeclarations(document, ctx);
                Json.Document visited = super.visitDocument(document, ctx);
                direct = previous;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String key = ReactReduxSupport.key(visited);
                String declaration = ReactReduxSupport.stringValue(visited);
                String section = ReactReduxSupport.directDependencySection(getCursor());

                if (ReactReduxSupport.PACKAGE.equals(key) && !section.isEmpty() &&
                    !ReactReduxSupport.target(declaration)) {
                    return markValue(visited, "This direct react-redux declaration is not the exact workbook target after strict migration; resolve ranges, protocols, aliases, forks, and unlisted versions deliberately");
                }
                if (ReactReduxSupport.packageSelector(key) && section.isEmpty() &&
                    ReactReduxSupport.underCentralOwnership(getCursor())) {
                    return SearchResult.found(visited,
                            "This central package-manager owner can select react-redux independently of the direct declaration; align it to 9.3.0 and regenerate the owning lockfile deliberately");
                }
                if (!direct.containsKey(ReactReduxSupport.PACKAGE) || section.isEmpty()) return visited;

                if (ReactReduxSupport.TYPES_PACKAGE.equals(key)) {
                    return markValue(visited, "React Redux 8+ ships its own TypeScript declarations; remove @types/react-redux and replace DefaultRootState augmentation with application-owned RootState and typed hooks");
                }
                if ("react".equals(key) && !supportsReact18Or19(declaration)) {
                    return markValue(visited, "React Redux 9 requires React 18 or 19; upgrade React and validate concurrent rendering, Strict Mode effects, hydration, and renderer alignment");
                }
                if ("react-dom".equals(key) && !supportsMajor(declaration, 18, 19, 0, 0)) {
                    return markValue(visited, "This ReactDOM declaration is below the React 18 runtime required by React Redux 9; align the renderer before validating Provider hydration");
                }
                if ("redux".equals(key) && !supportsMajor(declaration, 5, 5, 0, 0)) {
                    return markValue(visited, "React Redux 9.3.0 declares Redux ^5.0.0; upgrade Redux core and verify middleware, enhancers, reducer typing, UnknownAction, and legacy createStore usage");
                }
                if ("@reduxjs/toolkit".equals(key) && !supportsMajor(declaration, 2, 2, 0, 0)) {
                    return markValue(visited, "React Redux 9 belongs to the Redux Toolkit 2 / Redux 5 release wave; upgrade Toolkit and verify its Redux, thunk, middleware, and TypeScript changes");
                }
                if ("@types/react".equals(key) && !supportsMajor(declaration, 18, 19, 2, 25)) {
                    return markValue(visited, "React Redux 9.3.0 accepts @types/react ^18.2.25 or ^19; deduplicate and align React types to avoid Provider, children, and JSX type conflicts");
                }
                if ("typescript".equals(key) && !supportsTypeScript47(declaration)) {
                    return markValue(visited, "React Redux 9 drops TypeScript 4.6 and earlier; use TypeScript 4.7+ and recheck connect, hooks, context, JSX, and Redux 5 UnknownAction inference");
                }
                if (ReactReduxSupport.PACKAGE.equals(key) && ReactReduxSupport.target(declaration)) {
                    boolean missingReact = !direct.containsKey("react");
                    boolean missingRedux = !direct.containsKey("redux") && !direct.containsKey("@reduxjs/toolkit");
                    if (missingReact || missingRedux) {
                        String missing = missingReact && missingRedux ? "React 18/19 and Redux 5 (or Redux Toolkit 2)" :
                                missingReact ? "React 18/19" : "Redux 5 or Redux Toolkit 2";
                        return markValue(visited, "React Redux 9 requires an application-owned compatible peer declaration; add or verify " + missing + " in this workspace package");
                    }
                }
                return visited;
            }

            private Json.Member markValue(Json.Member member, String message) {
                return member.withValue(SearchResult.found(member.getValue(), message));
            }

            private Map<String, String> directDeclarations(Json.Document document, ExecutionContext ctx) {
                Map<String, String> values = new HashMap<>();
                new JsonIsoVisitor<ExecutionContext>() {
                    @Override
                    public Json.Member visitMember(Json.Member member, ExecutionContext scanCtx) {
                        Json.Member visited = super.visitMember(member, scanCtx);
                        String value = ReactReduxSupport.stringValue(visited);
                        if (value != null && !ReactReduxSupport.directDependencySection(getCursor()).isEmpty()) {
                            values.putIfAbsent(ReactReduxSupport.key(visited), value);
                        }
                        return visited;
                    }
                }.visit(document, ctx);
                return values;
            }
        };
    }

    private static boolean supportsReact18Or19(String declaration) {
        return supportsMajor(declaration, 18, 19, 0, 0);
    }

    private static boolean supportsMajor(String declaration, int minimumMajor, int maximumMajor,
                                         int minimumMinor, int minimumPatch) {
        if (declaration == null) return false;
        String value = declaration.trim();
        if (value.isEmpty() || value.contains("||") || value.contains("workspace:") ||
            value.contains("npm:") || value.contains("file:") || value.contains("link:") ||
            value.contains("git") || value.contains("*") || value.toLowerCase().contains("x")) return false;
        Matcher matcher = LOWER_BOUND.matcher(value);
        if (!matcher.matches()) return false;
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        if (major < minimumMajor || major > maximumMajor) return false;
        if (major == minimumMajor && (minor < minimumMinor || minor == minimumMinor && patch < minimumPatch)) {
            return false;
        }
        if (value.startsWith(">=") || value.startsWith(">")) {
            String expectedUpper = "<" + (maximumMajor + 1);
            return value.contains(expectedUpper) || maximumMajor == Integer.MAX_VALUE;
        }
        return value.matches("^(?:\\^|~)?\\d+(?:\\.\\d+){0,2}$");
    }

    private static boolean supportsTypeScript47(String declaration) {
        if (declaration == null) return false;
        String value = declaration.trim();
        if (value.contains("||") || value.contains("workspace:") || value.contains("npm:") ||
            value.contains("*") || value.toLowerCase().contains("x")) return false;
        Matcher matcher = LOWER_BOUND.matcher(value);
        if (!matcher.matches()) return false;
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return major > 4 || major == 4 && minor >= 7;
    }
}
