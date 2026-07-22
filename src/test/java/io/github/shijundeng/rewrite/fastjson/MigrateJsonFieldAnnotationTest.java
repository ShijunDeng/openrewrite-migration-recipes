package io.github.shijundeng.rewrite.fastjson;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class MigrateJsonFieldAnnotationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MigrateJsonFieldAnnotation())
                .parser(JavaParser.fromJavaVersion().classpath("fastjson", "jackson-annotations"));
    }

    @Test
    void migratesNameFormatAndAccess() {
        rewriteRun(
                java(
                        """
                        package example;

                        import com.alibaba.fastjson.annotation.JSONField;

                        class User {
                            @JSONField(name = "user_name")
                            String name;

                            @JSONField(name = "created_at", format = "yyyy-MM-dd")
                            java.util.Date createdAt;

                            @JSONField(serialize = false)
                            String writeOnlySecret;

                            @JSONField(serialize = false, deserialize = false)
                            String ignored;
                        }
                        """,
                        """
                        package example;

                        import com.fasterxml.jackson.annotation.JsonFormat;
                        import com.fasterxml.jackson.annotation.JsonIgnore;
                        import com.fasterxml.jackson.annotation.JsonProperty;

                        class User {
                            @JsonProperty(value = "user_name")
                            String name;

                            @JsonProperty(value = "created_at")
                            @JsonFormat(pattern = "yyyy-MM-dd")
                            java.util.Date createdAt;

                            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
                            String writeOnlySecret;

                            @JsonIgnore
                            String ignored;
                        }
                        """
                )
        );
    }
}
