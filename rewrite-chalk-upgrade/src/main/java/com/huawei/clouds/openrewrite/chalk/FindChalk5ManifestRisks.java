package com.huawei.clouds.openrewrite.chalk;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.json.JsonIsoVisitor;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.SearchResult;

import java.util.Locale;

/** Marks package ownership, ESM, runtime, compiler, and peer-contract risks. */
public final class FindChalk5ManifestRisks extends Recipe {
    static final String ESM_OWNER_MESSAGE =
            "Chalk 5.6.2 is pure ESM; this package is not explicitly type=module, so migrate the module owner, entry points, tests, TypeScript/bundler resolution, CLI, SSR, Electron, and mocks before runtime rollout";
    static final String NODE_MESSAGE =
            "Chalk 5.6.2 declares Node ^12.17.0 || ^14.13 || >=16; align local, CI, container, serverless, Electron, and production runtimes with an allowed release";

    @Override
    public String getDisplayName() {
        return "Find Chalk 5 package and JSON configuration risks";
    }

    @Override
    public String getDescription() {
        return "Marks skipped dependency ownership, peer/optional contracts, CommonJS package/TypeScript settings, old Node/TypeScript baselines, and redundant Chalk types at exact JSON values.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JsonIsoVisitor<ExecutionContext>() {
            private boolean packageJson;
            private boolean chalkManifest;
            private boolean explicitModule;
            private boolean enginePresent;

            @Override
            public Json.Document visitDocument(Json.Document document, ExecutionContext ctx) {
                if (!ChalkSupport.isProjectPath(document.getSourcePath())) return document;
                boolean oldPackage = packageJson;
                boolean oldChalk = chalkManifest;
                boolean oldModule = explicitModule;
                boolean oldEngine = enginePresent;
                packageJson = ChalkSupport.isPackageJson(document.getSourcePath());
                chalkManifest = packageJson && containsDirectChalk(document);
                explicitModule = packageJson && "module".equals(rootString(document, "type"));
                enginePresent = packageJson && rootNestedString(document, "engines", "node") != null;
                Json.Document visited = super.visitDocument(document, ctx);
                packageJson = oldPackage;
                chalkManifest = oldChalk;
                explicitModule = oldModule;
                enginePresent = oldEngine;
                return visited;
            }

            @Override
            public Json.Member visitMember(Json.Member member, ExecutionContext ctx) {
                Json.Member visited = super.visitMember(member, ctx);
                String key = ChalkSupport.jsonKey(visited);
                String value = ChalkSupport.jsonString(visited);
                if (!packageJson) return configRisk(visited, key, value);
                if (!chalkManifest) return visited;
                String section = ChalkSupport.directDependencySection(getCursor());
                if (ChalkSupport.PACKAGE.equals(key) && !section.isEmpty()) {
                    if (ChalkSupport.selectedDeclaration(value) == null && !ChalkSupport.TARGET_VERSION.equals(value) &&
                        !("^" + ChalkSupport.TARGET_VERSION).equals(value) && !("~" + ChalkSupport.TARGET_VERSION).equals(value)) {
                        return markValue(visited,
                                "Strict Chalk migration skipped this unlisted, complex range, dynamic tag, alias, workspace, file, git, URL, patch, or protocol declaration; select a tested 5.6.2 constraint and regenerate the owning lockfile");
                    }
                    Json.Member result = visited;
                    if ("peerDependencies".equals(section)) result = markValue(result,
                            "Chalk 5 is pure ESM and this peer constraint is a published consumer contract; verify supported module systems, peer range, optional metadata, and downstream Node/TypeScript matrix");
                    if ("optionalDependencies".equals(section)) result = markValue(result,
                            "This optional Chalk runtime path becomes ESM-only; verify fallback behavior when resolution/loading fails across every supported package manager and deployment target");
                    if (!explicitModule) result = markValue(result, ESM_OWNER_MESSAGE);
                    if (!enginePresent) result = markValue(result, NODE_MESSAGE);
                    return result;
                }
                if (ChalkSupport.PACKAGE.equals(key) && section.isEmpty() && overrideOwner()) {
                    return markValue(visited,
                            "This Chalk version is owned by overrides/resolutions/pnpm/catalog/custom metadata rather than a direct dependency; update the real owner and regenerate every lockfile without forcing unrelated transitive consumers to ESM");
                }
                if ("@types/chalk".equals(key) && !section.isEmpty()) {
                    return markValue(visited,
                            "Chalk ships its own TypeScript declarations; remove or justify this redundant/stub @types/chalk owner and check type resolution for duplicate declarations");
                }
                if ("type".equals(key) && isRootMember() && "commonjs".equals(value)) {
                    return markValue(visited, ESM_OWNER_MESSAGE);
                }
                if ("node".equals(key) && isRootNestedMember("engines") && value != null && belowTargetNode(value)) {
                    return markValue(visited, NODE_MESSAGE);
                }
                if ("typescript".equals(key) && !section.isEmpty() && value != null && belowTypeScript47(value)) {
                    return markValue(visited,
                            "Chalk 5 ESM TypeScript consumers require TypeScript 4.7+; align compiler, module/moduleResolution, ts-node, test transforms, declarations, and build images");
                }
                return visited;
            }

            private Json.Member configRisk(Json.Member member, String key, String value) {
                Json.Document document = getCursor().firstEnclosingOrThrow(Json.Document.class);
                String file = document.getSourcePath().getFileName() == null ? "" :
                        document.getSourcePath().getFileName().toString().toLowerCase(Locale.ROOT);
                if (!file.startsWith("tsconfig") || !file.endsWith(".json")) return member;
                if ("module".equals(key) && "compilerOptions".equals(parentKey()) && value != null &&
                    ("commonjs".equalsIgnoreCase(value) || "amd".equalsIgnoreCase(value) || "umd".equalsIgnoreCase(value))) {
                    return markValue(member,
                            "Chalk 5 is pure ESM; this TypeScript module emit cannot load it synchronously, so move the owning build to Node16/NodeNext/ES modules and verify all entry points/tests");
                }
                if ("moduleResolution".equals(key) && "compilerOptions".equals(parentKey()) && value != null &&
                    ("classic".equalsIgnoreCase(value) || "node".equalsIgnoreCase(value) ||
                     "node10".equalsIgnoreCase(value))) {
                    return markValue(member,
                            "Chalk 5 uses an exports map and pure ESM; use a Node16/NodeNext/Bundler resolution mode appropriate to the runtime and verify conditional exports/types");
                }
                return member;
            }

            private String parentKey() {
                org.openrewrite.Cursor object = getCursor().getParent();
                org.openrewrite.Cursor parent = object == null ? null : object.getParent();
                return parent != null && parent.getValue() instanceof Json.Member member
                        ? ChalkSupport.jsonKey(member) : "";
            }

            private boolean isRootMember() {
                return getCursor().getParent() != null && getCursor().getParent().getParent() != null &&
                       getCursor().getParent().getParent().getValue() instanceof Json.Document;
            }

            private boolean isRootNestedMember(String owner) {
                org.openrewrite.Cursor object = getCursor().getParent();
                org.openrewrite.Cursor parent = object == null ? null : object.getParent();
                return parent != null && parent.getValue() instanceof Json.Member member &&
                       owner.equals(ChalkSupport.jsonKey(member)) && rootMember(parent);
            }

            private boolean overrideOwner() {
                org.openrewrite.Cursor object = getCursor().getParent();
                org.openrewrite.Cursor section = object == null ? null : object.getParent();
                if (section == null || !(section.getValue() instanceof Json.Member member)) return false;
                String name = ChalkSupport.jsonKey(member);
                if ((name.equals("overrides") || name.equals("resolutions") || name.equals("catalog")) &&
                        rootMember(section)) return true;
                if (!name.equals("overrides")) return false;
                org.openrewrite.Cursor pnpmObject = section.getParent();
                org.openrewrite.Cursor pnpm = pnpmObject == null ? null : pnpmObject.getParent();
                return pnpm != null && pnpm.getValue() instanceof Json.Member pnpmMember &&
                       "pnpm".equals(ChalkSupport.jsonKey(pnpmMember)) && rootMember(pnpm);
            }

            private boolean rootMember(org.openrewrite.Cursor member) {
                org.openrewrite.Cursor rootObject = member.getParent();
                org.openrewrite.Cursor document = rootObject == null ? null : rootObject.getParent();
                return document != null && document.getValue() instanceof Json.Document;
            }
        };
    }

    private static Json.Member markValue(Json.Member member, String message) {
        return member.getValue() instanceof Json.Literal
                ? member.withValue(SearchResult.mergingFound(member.getValue(), message, " | "))
                : SearchResult.mergingFound(member, message, " | ");
    }

    private static boolean containsDirectChalk(Json.Document document) {
        if (!(document.getValue() instanceof Json.JsonObject root)) return false;
        for (Json value : root.getMembers()) {
            if (!(value instanceof Json.Member section) ||
                !ChalkSupport.DEPENDENCY_SECTIONS.contains(ChalkSupport.jsonKey(section)) ||
                !(section.getValue() instanceof Json.JsonObject entries)) continue;
            for (Json entry : entries.getMembers()) {
                if (entry instanceof Json.Member member && ChalkSupport.PACKAGE.equals(ChalkSupport.jsonKey(member))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String rootString(Json.Document document, String key) {
        if (!(document.getValue() instanceof Json.JsonObject root)) return null;
        return root.getMembers().stream().filter(Json.Member.class::isInstance).map(Json.Member.class::cast)
                .filter(member -> key.equals(ChalkSupport.jsonKey(member))).map(ChalkSupport::jsonString)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private static String rootNestedString(Json.Document document, String owner, String key) {
        if (!(document.getValue() instanceof Json.JsonObject root)) return null;
        return root.getMembers().stream().filter(Json.Member.class::isInstance).map(Json.Member.class::cast)
                .filter(member -> owner.equals(ChalkSupport.jsonKey(member))).map(Json.Member::getValue)
                .filter(Json.JsonObject.class::isInstance).map(Json.JsonObject.class::cast)
                .flatMap(object -> object.getMembers().stream()).filter(Json.Member.class::isInstance)
                .map(Json.Member.class::cast).filter(member -> key.equals(ChalkSupport.jsonKey(member)))
                .map(ChalkSupport::jsonString).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    static boolean belowTargetNode(String declaration) {
        String compact = declaration.replace(" ", "").toLowerCase(Locale.ROOT);
        if (compact.contains("||")) {
            for (String branch : compact.split("[|][|]")) if (belowTargetNode(branch)) return true;
            return false;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(>=|[~^])?v?(\\d+)(?:[.](\\d+))?(?:[.]\\d+)?").matcher(compact);
        if (!matcher.matches()) return true;
        String operator = matcher.group(1) == null ? "" : matcher.group(1);
        int major = Integer.parseInt(matcher.group(2));
        int minor = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        if (major >= 16) return false;
        if (major == 14 && minor >= 13 || major == 12 && minor >= 17) {
            return !(operator.isEmpty() || operator.equals("^") || operator.equals("~"));
        }
        return true;
    }

    private static boolean belowTypeScript47(String declaration) {
        String value = declaration.replace(" ", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?:>=|[~^])?v?(\\d+)(?:[.](\\d+))?(?:[.]\\d+)?").matcher(value);
        if (!matcher.matches()) return true;
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return major < 4 || major == 4 && minor < 7;
    }
}
