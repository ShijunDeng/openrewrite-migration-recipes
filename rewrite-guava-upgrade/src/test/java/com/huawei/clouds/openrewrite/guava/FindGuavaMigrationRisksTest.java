package com.huawei.clouds.openrewrite.guava;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class FindGuavaMigrationRisksTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindGuavaMigrationRisks())
                .parser(JavaParser.fromJavaVersion().classpath("guava"));
    }

    @Test
    void marksRemovedAndBehaviorSensitiveMethodsPrecisely() {
        rewriteRun(
                java(
                        """
                        import com.google.common.base.Predicates;
                        import com.google.common.hash.HashFunction;
                        import com.google.common.hash.Hashing;
                        import com.google.common.io.Files;
                        import com.google.common.util.concurrent.Futures;
                        import com.google.common.util.concurrent.ListenableFuture;
                        import java.io.File;

                        class Risks {
                            void calls(ListenableFuture<ListenableFuture<String>> nested) {
                                Predicates.assignableFrom(Number.class);
                                Futures.dereference(nested);
                                File temp = Files.createTempDir();
                                HashFunction hash = Hashing.murmur3_32();
                            }
                        }
                        """,
                        """
                        import com.google.common.base.Predicates;
                        import com.google.common.hash.HashFunction;
                        import com.google.common.hash.Hashing;
                        import com.google.common.io.Files;
                        import com.google.common.util.concurrent.Futures;
                        import com.google.common.util.concurrent.ListenableFuture;
                        import java.io.File;

                        class Risks {
                            void calls(ListenableFuture<ListenableFuture<String>> nested) {
                                /*~~(Predicates.assignableFrom was removed; replace it with an application-reviewed Class::isAssignableFrom predicate)~~>*/Predicates.assignableFrom(Number.class);
                                /*~~(Futures.dereference was removed; review cancellation and exception propagation before replacing it with transformAsync)~~>*/Futures.dereference(nested);
                                File temp = /*~~(Files.createTempDir is deprecated and changed security/error behavior; migrate with explicit IOException and permissions handling)~~>*/Files.createTempDir();
                                HashFunction hash = /*~~(murmur3_32 is deprecated; changing to murmur3_32_fixed can change persisted, partitioning, or interoperability hashes)~~>*/Hashing.murmur3_32();
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksRemovedCheckedFutureType() {
        rewriteRun(
                java(
                        """
                        import com.google.common.collect.BinaryTreeTraverser;
                        import com.google.common.util.concurrent.CheckedFuture;

                        class LegacyApi {
                            CheckedFuture<String, Exception> result;
                            BinaryTreeTraverser<String> traverser;
                        }
                        """,
                        """
                        import com.google.common.collect.BinaryTreeTraverser;
                        import com.google.common.util.concurrent.CheckedFuture;

                        class LegacyApi {
                            /*~~(CheckedFuture was removed; define exception conversion explicitly at the application boundary)~~>*/CheckedFuture<String, Exception> result;
                            /*~~(BinaryTreeTraverser was removed; migrate the traversal contract to Traverser after reviewing graph/tree semantics)~~>*/BinaryTreeTraverser<String> traverser;
                        }
                        """
                )
        );
    }

    @Test
    void marksTraversalAndGraphChoices() {
        rewriteRun(
                java(
                        """
                        import com.google.common.graph.Graph;
                        import com.google.common.graph.Graphs;
                        import com.google.common.io.Files;
                        import com.google.common.io.MoreFiles;

                        class MoreRisks {
                            void calls(Graph<String> left, Graph<String> right) {
                                Files.fileTreeTraverser();
                                MoreFiles.directoryTreeTraverser();
                                Graphs.equivalent(left, right);
                            }
                        }
                        """,
                        """
                        import com.google.common.graph.Graph;
                        import com.google.common.graph.Graphs;
                        import com.google.common.io.Files;
                        import com.google.common.io.MoreFiles;

                        class MoreRisks {
                            void calls(Graph<String> left, Graph<String> right) {
                                /*~~(Files.fileTreeTraverser was removed; choose MoreFiles.fileTraverser or Files.walk and review symlink/error handling)~~>*/Files.fileTreeTraverser();
                                /*~~(MoreFiles.directoryTreeTraverser was removed; migrate to fileTraverser and review traversal error handling)~~>*/MoreFiles.directoryTreeTraverser();
                                /*~~(Graphs.equivalent was removed; choose graph equality semantics explicitly)~~>*/Graphs.equivalent(left, right);
                            }
                        }
                        """
                )
        );
    }

    @Test
    void marksObsoleteGwtRpcPropertyLiteral() {
        rewriteRun(
                java(
                        """
                        class GwtConfig {
                            String property = "guava.gwt.emergency_reenable_rpc";
                        }
                        """,
                        """
                        class GwtConfig {
                            String property = /*~~(Guava GWT-RPC support was removed; this emergency property no longer restores it)~~>*/"guava.gwt.emergency_reenable_rpc";
                        }
                        """
                )
        );
    }

    @Test
    void leavesSameNamedApplicationMethodsUnmarked() {
        rewriteRun(
                java(
                        """
                        import com.google.common.net.HostAndPort;

                        class ApplicationHashing {
                            Object murmur3_32() { return new Object(); }
                            void call() {
                                murmur3_32();
                                HostAndPort.fromString("[::1]:443");
                            }
                        }
                        """
                )
        );
    }
}
