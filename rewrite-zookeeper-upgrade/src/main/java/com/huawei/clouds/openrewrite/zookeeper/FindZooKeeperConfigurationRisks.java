package com.huawei.clouds.openrewrite.zookeeper;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.properties.PropertiesIsoVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.yaml.YamlIsoVisitor;
import org.openrewrite.yaml.tree.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Structured markers for ZooKeeper server/client settings that need operational evidence. */
public final class FindZooKeeperConfigurationRisks extends Recipe {
    static final String DATA =
            "ZooKeeper persistence setting: verify dataDir/dataLogDir ownership, snapshot.trust.empty, checksums, " +
            "autopurge retention, disk capacity, permissions and a tested backup/restore before the rolling upgrade";
    static final String QUORUM =
            "ZooKeeper quorum/reconfiguration setting: verify server.N client ports, dynamicConfigFile, election " +
            "ports, reconfig/standalone policy, upgrade order and rollback on an ensemble clone";
    static final String NETWORK =
            "ZooKeeper listener/admin/4lw setting: verify bind addresses, secure/plain port unification, command " +
            "allow-list, admin endpoint exposure and monitoring behavior in the final deployment";
    static final String SECURITY =
            "ZooKeeper TLS/SASL/auth setting: verify principals, canonicalization, FIPS mode, keystore/truststore " +
            "reload, clientAuth, hostname/reverse-DNS behavior, secrets and mixed-version connections";
    static final String AUDIT =
            "ZooKeeper audit setting: verify SLF4J 2 provider routing, audit enablement, implementation class, event " +
            "completeness, credential redaction, retention and startup behavior";

    @Override
    public String getDisplayName() {
        return "Find ZooKeeper 3.8.6 configuration risks";
    }

    @Override
    public String getDescription() {
        return "Mark structured Properties and YAML persistence, quorum, listener/admin, TLS/SASL/auth and audit " +
               "settings that require deployment evidence instead of speculative rewrites.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source) ||
                    ZooKeeperSupport.generated(source.getSourcePath())) return tree;
                if (tree instanceof Properties.File properties) return properties(properties, ctx);
                if (tree instanceof Yaml.Documents yaml) return yaml(yaml, ctx);
                return tree;
            }
        };
    }

    private static Properties.File properties(Properties.File source, ExecutionContext ctx) {
        return (Properties.File) new PropertiesIsoVisitor<ExecutionContext>() {
            @Override
            public Properties.Entry visitEntry(Properties.Entry entry, ExecutionContext ec) {
                Properties.Entry visited = super.visitEntry(entry, ec);
                String message = message(visited.getKey());
                return message == null ? visited : mark(visited, message);
            }
        }.visitNonNull(source, ctx);
    }

    private static Yaml.Documents yaml(Yaml.Documents source, ExecutionContext ctx) {
        return (Yaml.Documents) new YamlIsoVisitor<ExecutionContext>() {
            @Override
            public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, ExecutionContext ec) {
                Yaml.Mapping.Entry visited = super.visitMappingEntry(entry, ec);
                if (!(visited.getValue() instanceof Yaml.Scalar)) return visited;
                String message = message(path());
                return message == null ? visited : mark(visited, message);
            }

            private String path() {
                List<String> keys = new ArrayList<>();
                getCursor().getPathAsStream().filter(Yaml.Mapping.Entry.class::isInstance)
                        .map(Yaml.Mapping.Entry.class::cast)
                        .forEach(entry -> keys.add(entry.getKey().getValue()));
                Collections.reverse(keys);
                return String.join(".", keys);
            }
        }.visitNonNull(source, ctx);
    }

    static String message(String raw) {
        String key = canonical(raw);
        if (key.contains("datadir") || key.contains("datalogdir") || key.contains("snapshot.trust.empty") ||
            key.contains("autopurge.") || key.contains("snapcount") || key.contains("fsync.warningthreshold")) {
            return DATA;
        }
        if (key.matches("(?:zookeeper\\.)?server\\.\\d+.*") || key.contains("dynamicconfigfile") ||
            key.contains("reconfigenabled") || key.contains("standaloneenabled") ||
            key.contains("initlimit") || key.contains("synclimit") || key.contains("electionalg")) {
            return QUORUM;
        }
        if (key.contains("clientport") || key.contains("secureclientport") ||
            key.contains("portunification") || key.contains("4lw.commands.whitelist") ||
            key.contains("admin.") || key.contains("metricsprovider.")) {
            return NETWORK;
        }
        if (key.contains("ssl.") || key.contains("sasl") || key.contains("kerberos") ||
            key.contains("authprovider") || key.contains("superdigest") ||
            key.contains("client.secure") || key.contains("fips.mode")) {
            return SECURITY;
        }
        if (key.contains("audit.enable") || key.contains("audit.impl.class")) return AUDIT;
        return null;
    }

    private static String canonical(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replace('_', '.').replace('-', '.');
    }

    private static <T extends Tree> T mark(T tree, String message) {
        return tree.getMarkers().findAll(SearchResult.class).stream()
                .anyMatch(result -> message.equals(result.getDescription())) ? tree :
                SearchResult.found(tree, message);
    }
}
