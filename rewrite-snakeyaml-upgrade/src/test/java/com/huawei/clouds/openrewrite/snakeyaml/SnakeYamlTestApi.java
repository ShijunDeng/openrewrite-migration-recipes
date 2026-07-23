package com.huawei.clouds.openrewrite.snakeyaml;

final class SnakeYamlTestApi {
    private SnakeYamlTestApi() {
    }

    static String[] sources() {
        return new String[]{
                """
                package org.yaml.snakeyaml;
                public class LoaderOptions {
                    public void setAllowDuplicateKeys(boolean value) { }
                    public void setWarnOnDuplicateKeys(boolean value) { }
                    public void setWrappedToRootException(boolean value) { }
                    public void setMaxAliasesForCollections(int value) { }
                    public void setAllowRecursiveKeys(boolean value) { }
                    public void setProcessComments(boolean value) { }
                    public void setEnumCaseSensitive(boolean value) { }
                    public void setNestingDepthLimit(int value) { }
                    public void setCodePointLimit(int value) { }
                    public void setMergeOnCompose(boolean value) { }
                    public void setTagInspector(org.yaml.snakeyaml.inspector.TagInspector value) { }
                }
                """,
                "package org.yaml.snakeyaml; public class DumperOptions { }",
                "package org.yaml.snakeyaml; public class TypeDescription { public TypeDescription(Class<?> type) { } }",
                """
                package org.yaml.snakeyaml;
                public class Yaml {
                    public Yaml() { }
                    public Yaml(LoaderOptions options) { }
                    public Yaml(DumperOptions options) { }
                    public Yaml(org.yaml.snakeyaml.constructor.BaseConstructor constructor) { }
                    public Yaml(org.yaml.snakeyaml.constructor.BaseConstructor constructor,
                                org.yaml.snakeyaml.representer.Representer representer,
                                DumperOptions dumperOptions) { }
                    public <T> T load(String value) { return null; }
                    public <T> T loadAs(String value, Class<? super T> type) { return null; }
                    public Iterable<Object> loadAll(String value) { return null; }
                    public Object compose(java.io.Reader value) { return null; }
                    public String dump(Object value) { return null; }
                    public Object represent(Object value) { return null; }
                    public void addImplicitResolver(Object tag, java.util.regex.Pattern pattern, String first) { }
                }
                """,
                """
                package org.yaml.snakeyaml.constructor;
                public abstract class BaseConstructor {
                    public void addTypeDescription(org.yaml.snakeyaml.TypeDescription value) { }
                    public org.yaml.snakeyaml.introspector.PropertyUtils getPropertyUtils() { return null; }
                }
                """,
                """
                package org.yaml.snakeyaml.constructor;
                public class SafeConstructor extends BaseConstructor {
                    public SafeConstructor() { }
                    public SafeConstructor(org.yaml.snakeyaml.LoaderOptions options) { }
                }
                """,
                """
                package org.yaml.snakeyaml.constructor;
                public class Constructor extends SafeConstructor {
                    public Constructor() { }
                    public Constructor(org.yaml.snakeyaml.LoaderOptions options) { }
                    public Constructor(Class<?> type) { }
                    public Constructor(Class<?> type, org.yaml.snakeyaml.LoaderOptions options) { }
                    public Constructor(org.yaml.snakeyaml.TypeDescription type) { }
                    public Constructor(org.yaml.snakeyaml.TypeDescription type, org.yaml.snakeyaml.LoaderOptions options) { }
                    public Constructor(org.yaml.snakeyaml.TypeDescription type,
                                       java.util.Collection<org.yaml.snakeyaml.TypeDescription> more) { }
                    public Constructor(org.yaml.snakeyaml.TypeDescription type,
                                       java.util.Collection<org.yaml.snakeyaml.TypeDescription> more,
                                       org.yaml.snakeyaml.LoaderOptions options) { }
                    public Constructor(String className) throws ClassNotFoundException { }
                    public Constructor(String className, org.yaml.snakeyaml.LoaderOptions options)
                            throws ClassNotFoundException { }
                }
                """,
                """
                package org.yaml.snakeyaml.representer;
                public class Representer {
                    public Representer() { }
                    public Representer(org.yaml.snakeyaml.DumperOptions options) { }
                    public void addTypeDescription(org.yaml.snakeyaml.TypeDescription value) { }
                    public void addClassTag(Class<?> type, Object tag) { }
                }
                """,
                """
                package org.yaml.snakeyaml.introspector;
                public class PropertyUtils {
                    public void setSkipMissingProperties(boolean value) { }
                    public void setAllowReadOnlyProperties(boolean value) { }
                }
                """,
                "package org.yaml.snakeyaml.inspector; public interface TagInspector { boolean isGlobalTagAllowed(Object tag); }"
        };
    }
}
