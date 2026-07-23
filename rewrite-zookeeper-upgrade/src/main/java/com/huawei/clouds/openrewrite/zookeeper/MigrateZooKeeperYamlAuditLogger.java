package com.huawei.clouds.openrewrite.zookeeper;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact YAML equivalent of the official Properties ChangePropertyValue building block. */
public final class MigrateZooKeeperYamlAuditLogger extends Recipe {
    static final String KEY = "zookeeper.audit.impl.class";
    static final String OLD = "org.apache.zookeeper.audit.Log4jAuditLogger";
    static final String NEW = "org.apache.zookeeper.audit.Slf4jAuditLogger";

    @Override
    public String getDisplayName() {
        return "Migrate the ZooKeeper YAML audit logger implementation";
    }

    @Override
    public String getDescription() {
        return "Change only an exact zookeeper.audit.impl.class YAML value from Log4jAuditLogger to " +
               "Slf4jAuditLogger, matching the deterministic ZooKeeper 3.8 implementation rename.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    ZooKeeperSupport.generated(source.getSourcePath()) ||
                    !(tree instanceof Yaml.Documents yaml)) return tree;
                return new YamlIsoVisitor<ExecutionContext>() {
                    @Override
                    public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                        Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ec);
                        if (!(visited.getValue() instanceof Yaml.Scalar scalar) ||
                            !KEY.equals(path()) || !OLD.equals(scalar.getValue())) return visited;
                        return visited.withValue(scalar.withValue(NEW));
                    }

                    private String path() {
                        List<String> keys = new ArrayList<>();
                        getCursor().getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                                .map(Yaml.Mapping.Entry.class::cast)
                                .forEach(mapping -> keys.add(mapping.getKey().getValue()));
                        Collections.reverse(keys);
                        return String.join(".", keys);
                    }
                }.visitNonNull(yaml, ctx);
            }
        };
    }
}
