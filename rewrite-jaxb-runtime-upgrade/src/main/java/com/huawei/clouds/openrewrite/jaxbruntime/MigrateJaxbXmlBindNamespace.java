package com.huawei.clouds.openrewrite.jaxbruntime;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangePackage;
import org.openrewrite.java.search.UsesType;

/** Migrate JAXB Javax types while preserving compilation units that still use the removed Validator API. */
public final class MigrateJaxbXmlBindNamespace extends Recipe {
    @Override
    public String getDisplayName() {
        return "Migrate JAXB Javax types to Jakarta except removed Validator usages";
    }

    @Override
    public String getDescription() {
        return "Change javax.xml.bind packages to jakarta.xml.bind, but leave a compilation unit unchanged when " +
               "it still uses the removed javax.xml.bind.Validator API so the migration does not invent a " +
               "nonexistent jakarta.xml.bind.Validator type.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.not(new UsesType<>("javax.xml.bind.Validator", false)),
                new ChangePackage("javax.xml.bind", "jakarta.xml.bind", true).getVisitor());
    }
}
