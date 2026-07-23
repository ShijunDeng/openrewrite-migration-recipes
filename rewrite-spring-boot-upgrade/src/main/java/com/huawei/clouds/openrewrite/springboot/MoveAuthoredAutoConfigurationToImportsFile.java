package com.huawei.clouds.openrewrite.springboot;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.spring.boot2.MoveAutoConfigurationToImportsFile;

import java.util.Collection;

/**
 * Generated-path guard around the official scanning recipe. Declarative
 * preconditions only protect edit visitors, so scanning and generation need
 * this adapter to prevent generated spring.factories files from producing
 * authored output.
 */
public final class MoveAuthoredAutoConfigurationToImportsFile extends ScanningRecipe<Object> {
    private final ScanningRecipe<Object> official = officialRecipe();

    @Override
    public String getDisplayName() {
        return "Move authored Spring Boot auto-configuration registrations";
    }

    @Override
    public String getDescription() {
        return "Delegate scanning, generation, and edits to the official MoveAutoConfigurationToImportsFile " +
               "recipe while excluding generated project paths.";
    }

    @Override
    public Object getInitialValue(ExecutionContext ctx) {
        return official.getInitialValue(ctx);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Object accumulator) {
        return authoredOnly(official.getScanner(accumulator));
    }

    @Override
    public Collection<? extends SourceFile> generate(Object accumulator, ExecutionContext ctx) {
        return official.generate(accumulator, ctx);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Object accumulator) {
        return authoredOnly(official.getVisitor(accumulator));
    }

    Recipe officialDelegate() {
        return official;
    }

    private static TreeVisitor<?, ExecutionContext> authoredOnly(
            TreeVisitor<?, ExecutionContext> delegate) {
        return Preconditions.check(
                new FindAuthoredSpringBootSourceFiles().getVisitor(), delegate);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ScanningRecipe<Object> officialRecipe() {
        return (ScanningRecipe) new MoveAutoConfigurationToImportsFile(false);
    }
}
