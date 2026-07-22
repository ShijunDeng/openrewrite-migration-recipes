package com.huawei.clouds.openrewrite.reactresizable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.test.RewriteTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.typescript;

class ReactResizableSourceTest implements RewriteTest {
    @Test
    void migratesOfficial111ExampleHandleAtFixedCommit() {
        // react-grid-layout/react-resizable@eeefa1a15d85c671c133c25da93c62e642966661, examples/ExampleLayout.js#L68-L85
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactResizableHandles()),
                javascript(
                        """
                        import { ResizableBox } from 'react-resizable';
                        const box = <ResizableBox width={200} height={200}
                          handle={(h) => <span className={`custom-handle custom-handle-${h}`} />}
                          handleSize={[8, 8]} />;
                        """,
                        """
                        import { ResizableBox } from 'react-resizable';
                        const box = <ResizableBox width={200} height={200}
                          handle={(h, resizeHandleRef) => <span ref={resizeHandleRef} className={`custom-handle custom-handle-${h}`} />}
                          handleSize={[8, 8]} />;
                        """, source -> source.path("src/ExampleLayout.jsx")));
    }

    @ParameterizedTest(name = "migrates deterministic handle {0}")
    @MethodSource("deterministicHandles")
    void migratesOnlyDeterministicNativeInlineHandles(String label, String before, String after) {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactResizableHandles()),
                javascript(before, after, source -> source.path("src/" + label + ".jsx")));
    }

    static Stream<Arguments> deterministicHandles() {
        return Stream.of(
                Arguments.of("unparenthesized",
                        "import { Resizable } from 'react-resizable';\nconst x = <Resizable width={1} height={1} handle={axis => <div />} />;\n",
                        "import { Resizable } from 'react-resizable';\nconst x = <Resizable width={1} height={1} handle={(axis, resizeHandleRef) => <div ref={resizeHandleRef} />} />;\n"),
                Arguments.of("alias",
                        "import { Resizable as Size } from \"react-resizable\";\nconst x = <Size width={1} height={1} handle={(edge) => <button aria-label={edge} />} />;\n",
                        "import { Resizable as Size } from \"react-resizable\";\nconst x = <Size width={1} height={1} handle={(edge, resizeHandleRef) => <button ref={resizeHandleRef} aria-label={edge} />} />;\n"),
                Arguments.of("box",
                        "import { ResizableBox } from 'react-resizable';\nconst x = <ResizableBox width={10} height={20} handle={(h) => <span className={'h-' + h}/>} />;\n",
                        "import { ResizableBox } from 'react-resizable';\nconst x = <ResizableBox width={10} height={20} handle={(h, resizeHandleRef) => <span ref={resizeHandleRef} className={'h-' + h}/>} />;\n"),
                Arguments.of("two",
                        "import { Resizable, ResizableBox } from 'react-resizable';\nconst a=<Resizable width={1} height={1} handle={(x)=><i/>}/>; const b=<ResizableBox width={1} height={1} handle={y => <b/>}/>;\n",
                        "import { Resizable, ResizableBox } from 'react-resizable';\nconst a=<Resizable width={1} height={1} handle={(x, resizeHandleRef)=><i ref={resizeHandleRef}/>}/>; const b=<ResizableBox width={1} height={1} handle={(y, resizeHandleRef) => <b ref={resizeHandleRef}/>}/>;\n")
        );
    }

    @ParameterizedTest(name = "leaves unproven handle {0}")
    @MethodSource("automaticNoOps")
    void leavesUnprovenAndAlreadyModernHandlesUntouched(String label, String source) {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactResizableHandles()),
                label.endsWith("-ts") ? typescript(source, input -> input.path("src/" + label + ".tsx"))
                        : javascript(source, input -> input.path("src/" + label + ".jsx")));
    }

    static Stream<Arguments> automaticNoOps() {
        return Stream.of(
                Arguments.of("modern", "import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={(axis, ref)=><span ref={ref}/>}/>;"),
                Arguments.of("native", "import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={<span/>}/>;"),
                Arguments.of("component", "import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={<MyHandle/>}/>;"),
                Arguments.of("identifier", "import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={renderHandle}/>;"),
                Arguments.of("block", "import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={(axis)=>{ return <span/>; }}/>;"),
                Arguments.of("typed-ts", "import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={(axis: Axis)=><span/>}/>;"),
                Arguments.of("spread", "import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={(axis)=><span {...props}/>}/>;"),
                Arguments.of("existing-ref", "import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={(axis)=><span ref={outerRef}/>}/>;"),
                Arguments.of("default", "import Resizable from 'react-resizable'; const x=<Resizable width={1} height={1} handle={(axis)=><span/>}/>;"),
                Arguments.of("local", "const Resizable=Local; const x=<Resizable width={1} height={1} handle={(axis)=><span/>}/>;"),
                Arguments.of("shadowed", "import {Resizable} from 'react-resizable'; function f(Resizable){ return <Resizable width={1} height={1} handle={(axis)=><span/>}/>; }"),
                Arguments.of("function-shadow", "import {Resizable as Size} from 'react-resizable'; function outer(){ function Size(){} return <Size width={1} height={1} handle={(axis)=><span/>}/>; }"),
                Arguments.of("class-shadow", "import {Resizable as Size} from 'react-resizable'; function outer(){ class Size{} return <Size width={1} height={1} handle={(axis)=><span/>}/>; }"),
                Arguments.of("member", "import {Resizable} from 'react-resizable'; const x=<UI.Resizable width={1} height={1} handle={(axis)=><span/>}/>;"),
                Arguments.of("comment", "import {Resizable} from 'react-resizable'; // <Resizable handle={(axis)=><span/>}/>\nconst safe=1;")
        );
    }

    @Test
    void skipsGeneratedSources() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactResizableHandles()),
                javascript("import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={(axis)=><span/>}/>;",
                        source -> source.path("dist/generated.jsx")));
    }

    @Test
    void deterministicHandleMigrationIsIdempotent() {
        rewriteRun(spec -> spec.recipe(new MigrateDeterministicReactResizableHandles())
                        .cycles(2).expectedCyclesThatMakeChanges(1),
                javascript(
                        "import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={(axis)=><span/>}/>;",
                        "import {Resizable} from 'react-resizable'; const x=<Resizable width={1} height={1} handle={(axis, resizeHandleRef)=><span ref={resizeHandleRef}/>}/>;",
                        source -> source.path("src/idempotent.jsx")));
    }

    @ParameterizedTest(name = "marks source risk {0}")
    @MethodSource("sourceRisks")
    void marksExactOwnedSourceRisks(String label, String source, String message) {
        rewriteRun(spec -> spec.recipe(new FindReactResizableSourceRisks()),
                javascript(source, input -> input.path("src/" + label + ".jsx")
                        .after(actual -> actual).afterRecipe(after -> assertContains(after.printAll(), message))));
    }

    static Stream<Arguments> sourceRisks() {
        return Stream.of(
                Arguments.of("callback-handle", component("handle={(axis) => <span />}"), "passes a DOM ref"),
                Arguments.of("component-handle", component("handle={<MyHandle />}"), "must accept the injected handleAxis"),
                Arguments.of("dynamic-handle", component("handle={renderHandle}"), "passes a DOM ref"),
                Arguments.of("stop", component("onResizeStop={save}"), "last onResize size"),
                Arguments.of("resize", component("onResize={resize}"), "React 18 batching"),
                Arguments.of("start", component("onResizeStart={start}"), "React 18 batching"),
                Arguments.of("aspect", component("lockAspectRatio"), "rewrote lockAspectRatio"),
                Arguments.of("draggable", component("draggableOpts={{scale: 2}}"), "moves from ^4.0.3"),
                Arguments.of("handles", component("resizeHandles={['n','w']}"), "constraint/handle calculations"),
                Arguments.of("constraints", component("minConstraints={[10,10]} maxConstraints={[20,20]}"), "constraint/handle calculations"),
                Arguments.of("scale", component("transformScale={2}"), "constraint/handle calculations"),
                Arguments.of("size", "import {Resizable} from 'react-resizable'; const x=<Resizable handle={<span/>}/>;", "conditionally requires width and height"),
                Arguments.of("css-class", component("handle={(axis, ref)=><span ref={ref} className='react-resizable-handle'/>}"), "CSS and hit-target contract"),
                Arguments.of("wrong-modern-ref", component("handle={(axis, injectedRef)=><span ref={otherRef}/>}"), "passes a DOM ref"),
                Arguments.of("deep", "import R from 'react-resizable/lib/Resizable'; const x=1;", "private/deep import"),
                Arguments.of("require-deep", "const R=require('react-resizable/lib/Resizable');", "private/deep import"),
                Arguments.of("require-root", "const {Resizable}=require('react-resizable');", "CommonJS react-resizable root import")
        );
    }

    @Test
    void marksRealApacheShenyuDraggableBoundary() {
        // apache/shenyu-dashboard@fc7f61eb43b36e1beac2665737195ce0cf391177, src/components/SiderMenu/SiderMenu.js#L338-L356
        String source = "import {Resizable} from 'react-resizable'; const x=<Resizable width={300} height={0} draggableOpts={{enableUserSelectHack:false}} handle={<span className='react-resizable-handle'/>}/>;";
        rewriteRun(spec -> spec.recipe(new FindReactResizableSourceRisks()),
                javascript(source, input -> input.path("src/SiderMenu.js").after(actual -> actual).afterRecipe(after -> {
                    assertContains(after.printAll(), "moves from ^4.0.3");
                    assertContains(after.printAll(), "CSS and hit-target contract");
                })));
    }

    @Test
    void keepsRealCruiseModernHandleAndMarksOnlyCallbacks() {
        // cruise-automation/webviz@d13afdacaec0b48f983adcaca55b84c36c42a5f2, Resizable.js#L137-L150
        String source = "import {Resizable} from 'react-resizable'; const x=<Resizable width={200} height={200} handle={(axis, ref)=><div ref={ref} className={'handle-'+axis}/>} onResize={resize}/>;";
        rewriteRun(spec -> spec.recipe(new FindReactResizableSourceRisks()),
                javascript(source, input -> input.path("src/Resizable.jsx").after(actual -> actual).afterRecipe(after -> {
                    String printed = after.printAll();
                    assertContains(printed, "React 18 batching");
                    assertTrue(!printed.contains("not mounted on DragStart"), printed);
                })));
    }

    @Test
    void marksRealSkybrushForwardRefComponentForCrossDeclarationReview() {
        // skybrush-io/live@81ab701d5c6aaa49e4bfc6758d88f38e3ed058fa, src/components/ResizableBox.jsx#L61-L103
        String source = "import React from 'react'; import {Resizable} from 'react-resizable'; const ResizeHandle=React.forwardRef((p,ref)=><div ref={ref}/>); const x=<Resizable width={1} height={1} handle={<ResizeHandle/>}/>;";
        rewriteRun(spec -> spec.recipe(new FindReactResizableSourceRisks()),
                javascript(source, input -> input.path("src/ResizableBox.jsx").after(actual -> actual).afterRecipe(after ->
                        assertContains(after.printAll(), "must accept the injected handleAxis"))));
    }

    @Test
    void leavesUnownedSameNamedComponentsAndPublicCssImportAlone() {
        rewriteRun(spec -> spec.recipe(new FindReactResizableSourceRisks()),
                javascript("import 'react-resizable/css/styles.css'; const Resizable=Local; const x=<Resizable lockAspectRatio/>;",
                        source -> source.path("src/local.jsx")));
    }

    @Test
    void leavesMemberExpressionWithSamePropertyNameAlone() {
        rewriteRun(spec -> spec.recipe(new FindReactResizableSourceRisks()),
                javascript("import {Resizable} from 'react-resizable'; const x=<UI.Resizable lockAspectRatio/>;",
                        source -> source.path("src/member.jsx")));
    }

    private static String component(String attribute) {
        return "import {Resizable} from 'react-resizable'; const x=<Resizable width={100} height={100} " + attribute + "/>;";
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected: " + expected + "\nActual:\n" + actual);
    }
}
