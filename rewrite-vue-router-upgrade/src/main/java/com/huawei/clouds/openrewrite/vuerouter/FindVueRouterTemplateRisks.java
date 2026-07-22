package com.huawei.clouds.openrewrite.vuerouter;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.text.PlainText;
import org.openrewrite.text.PlainTextVisitor;

import java.util.List;
import java.util.regex.Pattern;

/** Marks Vue SFC/HTML/declaration boundaries not represented by the JavaScript AST. */
public final class FindVueRouterTemplateRisks extends Recipe {
    private static final List<VueRouterPlainTextSupport.Risk> RISKS = List.of(
            new VueRouterPlainTextSupport.Risk(
                    Pattern.compile("(?is)<router-link\\b[^>]*\\b(?:append|event|tag|exact)(?:\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+))?[^>]*>"),
                    "Vue Router 4 removed RouterLink append/event/tag/exact props; use custom + v-slot/useLink and verify relative URLs, events, semantics, and active state"),
            new VueRouterPlainTextSupport.Risk(
                    Pattern.compile("(?is)<router-view\\b(?![^>]*\\bv-slot\\s*=)[^>]*>\\s*<(?:transition|keep-alive)\\b"),
                    "Transition and keep-alive must render inside RouterView's Component slot; preserve keys, nested views, caching, transition timing, and hydration"),
            new VueRouterPlainTextSupport.Risk(
                    Pattern.compile("(?is)<router-view\\b[^>]*>\\s*(?!</router-view>)[^<\\s][\\s\\S]{0,240}?</router-view>"),
                    "Content passed directly to RouterView no longer becomes a route component slot; render Component through RouterView v-slot and pass content explicitly"),
            new VueRouterPlainTextSupport.Risk(
                    Pattern.compile("unplugin-vue-router/client"),
                    "Remove the obsolete unplugin-vue-router/client type reference and include the generated Vue Router 5 route-map declaration instead"),
            new VueRouterPlainTextSupport.Risk(
                    Pattern.compile("\\bVue\\s*[.]\\s*use\\s*\\(\\s*(?:Router|VueRouter)\\s*\\)"),
                    "Vue.use(Router) is a Vue 2 global install; migrate to Vue 3 createApp and app.use(router)"),
            new VueRouterPlainTextSupport.Risk(
                    Pattern.compile("\\bnew\\s+(?:Router|VueRouter)\\s*\\("),
                    "Vue Router 3 class construction was removed; migrate to createRouter with an explicit history factory")
    );

    @Override
    public String getDisplayName() {
        return "Find Vue Router 5 template and declaration risks";
    }

    @Override
    public String getDescription() {
        return "Marks removed RouterLink props, RouterView transition/slot structure, Vue 2 installation, legacy " +
               "construction, and obsolete client type references in Vue/HTML/declaration plain-text sources.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new PlainTextVisitor<ExecutionContext>() {
            @Override
            public PlainText visitText(PlainText text, ExecutionContext ctx) {
                PlainText visited = super.visitText(text, ctx);
                return VueRouterPlainTextSupport.isTemplateOrDeclaration(visited)
                        ? VueRouterPlainTextSupport.mark(visited, RISKS) : visited;
            }
        };
    }
}
