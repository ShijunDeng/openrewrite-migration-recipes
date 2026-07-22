package com.huawei.clouds.openrewrite.chalk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class ChalkManifestAndBuildRiskTest implements RewriteTest {
    @Test
    void marksSkippedComplexDependencyOwner() {
        manifestRisk("{\"dependencies\":{\"chalk\":\">=2.4.2 <6\"}}", "Strict Chalk migration skipped");
    }

    @Test
    void marksCommonJsPackageOwner() {
        manifestRisk("{\"type\":\"commonjs\",\"dependencies\":{\"chalk\":\"5.6.2\"}}", "pure ESM");
    }

    @Test
    void marksMissingExplicitModuleOwnerAtDependency() {
        manifestRisk("{\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"chalk\":\"5.6.2\"}}", "not explicitly type=module");
    }

    @Test
    void marksMissingNodeOwnerAtDependency() {
        manifestRisk("{\"type\":\"module\",\"dependencies\":{\"chalk\":\"5.6.2\"}}", "align local, CI, container");
    }

    @ParameterizedTest
    @ValueSource(strings = {">=4", ">=8", ">=10", "^12.16.0", "12.0.0", ">=12.17.0", "^14.12.0", ">=14.13.0", "15.0.0", ">=15", "<16", "workspace:*"})
    void marksUnsupportedNodeEngine(String engine) {
        manifestRisk("{\"type\":\"module\",\"engines\":{\"node\":\"" + engine + "\"},\"dependencies\":{\"chalk\":\"5.6.2\"}}",
                "declares Node");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.17.0", "^12.17.0", "~12.22.0", "14.13.0", "^14.13.0", "~14.21.0", ">=16", "18.20.0", "^20.0.0", "^12.17.0 || ^14.13 || >=16"})
    void acceptsOfficialTargetNodeRanges(String engine) {
        assertFalse(FindChalk5ManifestRisks.belowTargetNode(engine), engine);
    }

    @ParameterizedTest
    @ValueSource(strings = {"^3.9.0", "4.0.8", "~4.6.4"})
    void marksOldTypeScriptCompiler(String version) {
        manifestRisk("{\"type\":\"module\",\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"chalk\":\"5.6.2\"},\"devDependencies\":{\"typescript\":\"" + version + "\"}}",
                "TypeScript 4.7+");
    }

    @Test
    void marksPeerContract() {
        manifestRisk("{\"type\":\"module\",\"engines\":{\"node\":\">=18\"},\"peerDependencies\":{\"chalk\":\"^5.6.2\"}}",
                "published consumer contract");
    }

    @Test
    void marksOptionalRuntimeContract() {
        manifestRisk("{\"type\":\"module\",\"engines\":{\"node\":\">=18\"},\"optionalDependencies\":{\"chalk\":\"5.6.2\"}}",
                "optional Chalk runtime path");
    }

    @Test
    void marksOverrideOwnership() {
        manifestRisk("{\"dependencies\":{\"chalk\":\"5.6.2\"},\"overrides\":{\"chalk\":\"4.1.2\"}}",
                "overrides/resolutions/pnpm/catalog");
    }

    @Test
    void marksPnpmOverrideOwnership() {
        manifestRisk("{\"dependencies\":{\"chalk\":\"5.6.2\"},\"pnpm\":{\"overrides\":{\"chalk\":\"4.1.2\"}}}",
                "overrides/resolutions/pnpm/catalog");
    }

    @Test
    void leavesSameNamedCustomMetadataAlone() {
        rewriteRun(spec -> spec.recipe(new FindChalk5ManifestRisks()),
                json("{\"type\":\"module\",\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"chalk\":\"5.6.2\"},\"theme\":{\"chalk\":\"blue\",\"@types/chalk\":\"label\",\"engines\":{\"node\":\"10\"}}}",
                        source -> source.path("package.json")));
    }

    @Test
    void marksRedundantTypes() {
        manifestRisk("{\"dependencies\":{\"chalk\":\"5.6.2\"},\"devDependencies\":{\"@types/chalk\":\"2.2.4\"}}",
                "ships its own TypeScript declarations");
    }

    @Test
    void marksCommonJsTsconfigModule() {
        configRisk("{\"compilerOptions\":{\"module\":\"commonjs\"}}", "cannot load it synchronously");
    }

    @ParameterizedTest
    @ValueSource(strings = {"classic", "node", "node10"})
    void marksLegacyTsconfigResolution(String resolution) {
        configRisk("{\"compilerOptions\":{\"moduleResolution\":\"" + resolution + "\"}}", "exports map");
    }

    @Test
    void modernManifestAndTsconfigAreNoOp() {
        rewriteRun(spec -> spec.recipe(new FindChalk5ManifestRisks()),
                json("{\"type\":\"module\",\"engines\":{\"node\":\">=18\"},\"dependencies\":{\"chalk\":\"5.6.2\"},\"devDependencies\":{\"typescript\":\"^5.9.0\"}}",
                        source -> source.path("package.json")),
                json("{\"compilerOptions\":{\"module\":\"NodeNext\",\"moduleResolution\":\"NodeNext\"}}",
                        source -> source.path("tsconfig.json")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FROM node:10-alpine\n", "FROM node:12.16.3\n", "FROM node:13.14\n", "FROM node:14.12-bullseye AS build\n", "FROM node:15.14\n"})
    void marksOldNodeDockerOwner(String before) {
        buildRisk(before, "align local, CI, container", "Dockerfile");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RUN npm install chalk@2.4.2\n", "yarn add --dev chalk@^4.1.2\n", "pnpm add chalk@5.3.0\n"})
    void marksPackageManagerCommandOutsideManifest(String before) {
        buildRisk(before, "outside package.json", "scripts/install.sh");
    }

    @ParameterizedTest
    @ValueSource(strings = {"v10.24.1", "12.16.3", "v14.12.0"})
    void marksOldNodeVersionFiles(String version) {
        buildRisk(version + "\n", "declares Node", ".nvmrc");
    }

    @Test
    void supportedNodeBuildOwnersAndCommentsAreNoOp() {
        rewriteRun(spec -> spec.recipe(new FindChalk5BuildRisks()),
                text("FROM node:12.22-alpine\n", source -> source.path("Dockerfile")),
                text("v18.20.0\n", source -> source.path(".node-version")),
                text("# npm install chalk@2.4.2\necho 'npm install chalk@2.4.2'\n", source -> source.path("scripts/install.sh")));
    }

    @Test
    void marksDynamicNodeBuildOwners() {
        buildRisk("FROM node:lts-alpine\n", "dynamic Node image tag", "Dockerfile");
        buildRisk("lts/*\n", "declares Node", ".nvmrc");
    }

    @Test
    void ordinaryDocumentationIsNotTreatedAsBuildOwner() {
        rewriteRun(spec -> spec.recipe(new FindChalk5BuildRisks()),
                text("npm install chalk@2.4.2\n", source -> source.path("README.md")));
    }

    @Test
    void generatedBuildOwnersAreExcluded() {
        rewriteRun(spec -> spec.recipe(new FindChalk5BuildRisks()),
                text("FROM node:10\nRUN npm install chalk@2.4.2\n", source -> source.path("dist/Dockerfile")));
    }

    @Test
    void manifestMarkersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.recipe(new FindChalk5ManifestRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                json("{\"dependencies\":{\"chalk\":\"5.6.2\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(document -> {
                            assertEquals(1, occurrences(document.printAll(), "not explicitly type=module"));
                        })));
    }

    @Test
    void buildMarkersAreIdempotentAcrossTwoCycles() {
        rewriteRun(spec -> spec.recipe(new FindChalk5BuildRisks())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text("FROM node:15.14\n", source -> source.path("Dockerfile").after(actual -> actual)
                        .afterRecipe(file -> assertEquals(1, occurrences(file.printAll(), "declares Node")))));
    }

    private void manifestRisk(String before, String expected) {
        rewriteRun(spec -> spec.recipe(new FindChalk5ManifestRisks()), json(before,
                source -> source.path("package.json").after(actual -> actual).afterRecipe(document ->
                        assertTrue(document.printAll().contains(expected), document.printAll()))));
    }

    private void configRisk(String before, String expected) {
        rewriteRun(spec -> spec.recipe(new FindChalk5ManifestRisks()), json(before,
                source -> source.path("tsconfig.json").after(actual -> actual).afterRecipe(document ->
                        assertTrue(document.printAll().contains(expected), document.printAll()))));
    }

    private void buildRisk(String before, String expected, String path) {
        rewriteRun(spec -> spec.recipe(new FindChalk5BuildRisks()), text(before,
                source -> source.path(path).after(actual -> actual).afterRecipe(file ->
                        assertTrue(file.printAll().contains(expected), file.printAll()))));
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(token, index)) >= 0; index += token.length()) count++;
        return count;
    }
}
