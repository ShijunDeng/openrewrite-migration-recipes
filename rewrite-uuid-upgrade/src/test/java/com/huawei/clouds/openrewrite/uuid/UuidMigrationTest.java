package com.huawei.clouds.openrewrite.uuid;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;
import org.openrewrite.test.RewriteTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.json.Assertions.json;
import static org.openrewrite.test.SourceSpecs.text;

class UuidMigrationTest implements RewriteTest {
    private static final String DEPENDENCY = "com.huawei.clouds.openrewrite.uuid.UpgradeUuidTo13_0_2";
    private static final String SOURCE = "com.huawei.clouds.openrewrite.uuid.MigrateDeterministicUuidSourceTo13";
    private static final String TYPES = "com.huawei.clouds.openrewrite.uuid.RemoveRedundantUuidTypesFor13";
    private static final String AUDIT = "com.huawei.clouds.openrewrite.uuid.AuditUuid13Compatibility";
    private static final String MIGRATION = "com.huawei.clouds.openrewrite.uuid.MigrateUuidTo13_0_2";

    @Test
    void migratesLegacyDefaultFunctionSubpathsAndAliases() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        import uuidv1 from 'uuid/v1';
                        import v3 from "uuid/v3.js";
                        import uuidv4 from 'uuid/v4';
                        import makeV5 from "uuid/v5";
                        """,
                        """
                        import { v1 as uuidv1 } from 'uuid';
                        import { v3 } from "uuid";
                        import { v4 as uuidv4 } from 'uuid';
                        import { v5 as makeV5 } from "uuid";
                        """,
                        source -> source.path("src/id.ts")
                )
        );
    }

    @Test
    void migratesLegacyReExports() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "export { default as createId } from 'uuid/v4';\n",
                        "export { v4 as createId } from 'uuid';\n",
                        source -> source.path("src/public-api.js")
                )
        );
    }

    @Test
    void sourceMigrationIsIdempotent() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE))
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                text(
                        "import uuidv4 from 'uuid/v4';\nconst id = uuidv4();\n",
                        "import { v4 as uuidv4 } from 'uuid';\nconst id = uuidv4();\n",
                        source -> source.path("src/id.js")
                )
        );
    }

    @Test
    void compatibleRealShineoutNamedImportIsNoOp() {
        // sheinsight/shineout, fixed commit f75168569cbf87e269f0d37fee2e91b71e9b6ea1:
        // https://github.com/sheinsight/shineout/blob/f75168569cbf87e269f0d37fee2e91b71e9b6ea1/src/utils/uid.ts
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        "import { v4 as getUUid } from 'uuid';\nexport const getUid = () => getUUid();\n",
                        source -> source.path("src/utils/uid.ts")
                )
        );
    }

    @Test
    void sourceMigrationDoesNotEditCommentsStringsTemplatesRegexOrCommonJs() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(SOURCE)),
                text(
                        """
                        // import uuidv4 from 'uuid/v4';
                        const docs = "import uuidv4 from 'uuid/v4'";
                        const template = `import uuidv4 from 'uuid/v4'`;
                        const matcher = /uuid\\/v4/;
                        const uuidv4 = require('uuid/v4');
                        """,
                        source -> source.path("src/docs.js")
                )
        );
    }

    @Test
    void marksCommonJsAtExactRequireExpression() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "const { v4: uuidv4 } = require('uuid');\n",
                        "const { v4: uuidv4 } = ~~(uuid 12+ is ESM-only; replace CommonJS loading and verify Node, Jest, SSR, bundler, and published package module mode)~~>require('uuid');\n",
                        source -> source.path("server/id.cjs")
                )
        );
    }

    @Test
    void marksDefaultAndUnresolvedDeepImports() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "import uuid from 'uuid';\nimport { rng } from 'uuid/dist/rng.js';\n",
                        "~~(uuid 13 has named root exports only; choose the intended UUID function or constant instead of a default import)~~>import uuid from 'uuid';\nimport { rng } ~~(uuid 13 exports only its public root and package.json; replace this deep/internal import with an explicit root named export)~~>from 'uuid/dist/rng.js';\n",
                        source -> source.path("src/legacy.ts")
                )
        );
    }

    @Test
    void marksTimestampOptionsForImportedAliases() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "import { v1 as timeId, v7 } from 'uuid';\nconst a = timeId({msecs: now}); const b = v7({msecs: now});\n",
                        "import { v1 as timeId, v7 } from 'uuid';\nconst a = ~~(uuid 11 changed v1 options state semantics; verify uniqueness, monotonicity, fixed-time tests, random source, and clock sequence)~~>timeId({msecs: now}); const b = ~~(uuid 11 changed v7 options state semantics; verify uniqueness, monotonicity, fixed-time tests, random source, and clock sequence)~~>v7({msecs: now});\n",
                        source -> source.path("src/time.ts")
                )
        );
    }

    @Test
    void marksNamespaceTimestampOptions() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "import * as ids from 'uuid';\nconst id = ids.v6({msecs: now});\n",
                        "import * as ids from 'uuid';\nconst id = ~~(uuid 11 changed v6 options state semantics; verify uniqueness, monotonicity, fixed-time tests, random source, and clock sequence)~~>ids.v6({msecs: now});\n",
                        source -> source.path("src/time.ts")
                )
        );
    }

    @Test
    void marksNameBasedOutputBufferBounds() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "import { v5 as hashId } from 'uuid';\nconst id = hashId(name, namespace, buffer, offset);\n",
                        "import { v5 as hashId } from 'uuid';\nconst id = ~~(This v5 output-buffer overload crosses the fixed bounds-check behavior; test short buffers and invalid offsets expecting RangeError)~~>hashId(name, namespace, buffer, offset);\n",
                        source -> source.path("src/hash.ts")
                )
        );
    }

    @Test
    void noTimestampMarkerWithoutOptions() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "import { v1, v7 } from 'uuid';\nconst a = v1(); const b = v7();\n",
                        source -> source.path("src/time.ts")
                )
        );
    }

    @Test
    void marksReactNativeImportOrderingAtImport() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        "import { v4 as uuidv4 } from 'uuid';\n",
                        "~~(React Native/Expo must load react-native-get-random-values before every direct or transitive uuid import)~~>import { v4 as uuidv4 } from 'uuid';\n",
                        source -> source.path("src/index.native.ts")
                )
        );
    }

    @Test
    void auditDoesNotMatchCommentsStringsTemplatesOrRegex() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                text(
                        """
                        // require('uuid')
                        const docs = "import uuid from 'uuid'";
                        const template = `require('uuid/v4')`;
                        const matcher = /require\\('uuid'\\)/;
                        """,
                        source -> source.path("src/docs.ts")
                )
        );
    }

    @Test
    void removesSingleOrdinaryTypesDependencyForTarget() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(TYPES)),
                json(
                        "{\"dependencies\":{\"uuid\":\"13.0.2\"},\"devDependencies\":{\"@types/uuid\":\"^10.0.0\",\"typescript\":\"5.8.3\"}}",
                        "{\"dependencies\":{\"uuid\":\"13.0.2\"},\"devDependencies\":{\"typescript\":\"5.8.3\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void keepsProtocolOrDuplicateTypesOwnership() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(TYPES)),
                json(
                        "{\"dependencies\":{\"uuid\":\"13.0.2\"},\"devDependencies\":{\"@types/uuid\":\"npm:@company/uuid-types@1.0.0\"}}",
                        source -> source.path("package.json")
                ),
                json(
                        "{\"dependencies\":{\"uuid\":\"13.0.2\",\"@types/uuid\":\"10.0.0\"},\"devDependencies\":{\"@types/uuid\":\"10.0.0\"}}",
                        source -> source.path("packages/duplicate/package.json")
                )
        );
    }

    @Test
    void marksSkippedDependencyAtExactValue() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json(
                        "{\"dependencies\":{\"uuid\":\">=9.0.1 <13\"}}",
                        "{\"dependencies\":{\"uuid\":/*~~(Strict migration skipped this uuid range, prerelease, protocol, alias, dynamic tag, or unlisted version; select and lock the intended 13.0.2 constraint explicitly)~~>*/\">=9.0.1 <13\"}}",
                        source -> source.path("package.json")
                )
        );
    }

    @Test
    void marksOldNodeCommonJsAndReactNativeManifestValues() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json(
                        "{\"type\":\"commonjs\",\"engines\":{\"node\":\">=12.18.3\"},\"dependencies\":{\"uuid\":\"13.0.2\",\"react-native\":\"0.78.0\"}}",
                        source -> source.path("package.json").after(actual -> actual).afterRecipe(document -> {
                            String printed = document.printAll();
                            assertTrue(printed.contains("uuid 12+ is ESM-only while this package declares CommonJS"));
                            assertTrue(printed.contains("uuid 13 no longer supports these old Node lines"));
                            assertTrue(printed.contains("React Native/Expo requires crypto.getRandomValues"));
                        })
                )
        );
    }

    @Test
    void supportedEsmManifestHasNoRiskMarker() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(AUDIT)),
                json(
                        "{\"type\":\"module\",\"engines\":{\"node\":\">=20\"},\"dependencies\":{\"uuid\":\"13.0.2\"}}",
                        source -> source.path("package.json").afterRecipe(document ->
                                assertFalse(document.printAll().contains("~~>")))
                )
        );
    }

    @Test
    void recommendedRecipeUpgradesMigratesRemovesTypesAndMarksRemainingRisk() {
        rewriteRun(
                spec -> spec.recipe(environment().activateRecipes(MIGRATION)),
                json(
                        "{\"type\":\"module\",\"dependencies\":{\"uuid\":\"^9.0.1\"},\"devDependencies\":{\"@types/uuid\":\"10.0.0\"}}",
                        "{\"type\":\"module\",\"dependencies\":{\"uuid\":\"13.0.2\"},\"devDependencies\":{}}",
                        source -> source.path("package.json")
                ),
                text(
                        "import uuidv4 from 'uuid/v4';\nconst legacy = require('uuid');\n",
                        "import { v4 as uuidv4 } from 'uuid';\nconst legacy = ~~(uuid 12+ is ESM-only; replace CommonJS loading and verify Node, Jest, SSR, bundler, and published package module mode)~~>require('uuid');\n",
                        source -> source.path("src/id.ts")
                )
        );
    }

    @Test
    void publicRecipesAreDiscoverableValidAndNamed() {
        Environment environment = environment();
        for (String name : new String[]{DEPENDENCY, SOURCE, TYPES, AUDIT, MIGRATION}) {
            Recipe recipe = environment.activateRecipes(name);
            assertTrue(environment.listRecipes().stream().anyMatch(candidate -> name.equals(candidate.getName())));
            assertTrue(recipe.validateAll().stream().allMatch(validation -> validation.isValid()),
                    () -> name + ": " + recipe.validateAll());
            assertEquals(name, recipe.getName());
        }
    }

    private static Environment environment() {
        return Environment.builder()
                .scanRuntimeClasspath("com.huawei.clouds.openrewrite.uuid")
                .scanYamlResources()
                .build();
    }
}
