package com.huawei.clouds.openrewrite.jakartael;

final class JakartaElTestApi {
    private JakartaElTestApi() {
    }

    static String[] legacySources() {
        return new String[]{
                "package javax.el; public class ELContext { public void setPropertyResolved(boolean value){} }",
                """
                package javax.el;
                public abstract class ELResolver {
                    public static final String TYPE = "type";
                    public static final String RESOLVABLE_AT_DESIGN_TIME = "resolvableAtDesignTime";
                    public abstract Object getValue(ELContext c, Object b, Object p);
                    public abstract Class<?> getType(ELContext c, Object b, Object p);
                    public abstract void setValue(ELContext c, Object b, Object p, Object v);
                    public abstract boolean isReadOnly(ELContext c, Object b, Object p);
                    public abstract java.util.Iterator<java.beans.FeatureDescriptor> getFeatureDescriptors(ELContext c, Object b);
                    public abstract Class<?> getCommonPropertyType(ELContext c, Object b);
                    public Object convertToType(ELContext c, Object value, Class<?> targetType) { return null; }
                }
                """,
                "package javax.el; public abstract class Expression implements java.io.Serializable { }",
                "package javax.el; public abstract class ValueExpression extends Expression { public abstract Object getValue(ELContext c); }",
                """
                package javax.el;
                public abstract class MethodExpression extends Expression {
                    public boolean isParametersProvided() { return false; }
                    @Deprecated public boolean isParmetersProvided() { return isParametersProvided(); }
                }
                """,
                "package javax.el; public class ArrayELResolver extends ELResolver { public Object getValue(ELContext c,Object b,Object p){return null;} public Class<?> getType(ELContext c,Object b,Object p){return null;} public void setValue(ELContext c,Object b,Object p,Object v){} public boolean isReadOnly(ELContext c,Object b,Object p){return false;} public java.util.Iterator<java.beans.FeatureDescriptor> getFeatureDescriptors(ELContext c,Object b){return null;} public Class<?> getCommonPropertyType(ELContext c,Object b){return null;} }",
                "package javax.el; public class StandardELContext extends ELContext { public StandardELContext(ExpressionFactory f){} }",
                "package javax.el; public class ImportHandler { public void importClass(String n){} public void importPackage(String n){} public void importStatic(String n){} public Class<?> resolveClass(String n){return null;} public Class<?> resolveStatic(String n){return null;} }",
                "package javax.el; public abstract class ExpressionFactory { public static ExpressionFactory newInstance(){return null;} public abstract ValueExpression createValueExpression(ELContext c,String e,Class<?> t); public abstract MethodExpression createMethodExpression(ELContext c,String e,Class<?> t,Class<?>[] p); }",
                "package javax.el; public class ELProcessor { public Object eval(String expression){return null;} }"
        };
    }

    static String[] targetSources() {
        return new String[]{
                "package jakarta.el; public class ELContext { public void setPropertyResolved(boolean value){} }",
                """
                package jakarta.el;
                public abstract class ELResolver {
                    public abstract Object getValue(ELContext c, Object b, Object p);
                    public abstract Class<?> getType(ELContext c, Object b, Object p);
                    public abstract void setValue(ELContext c, Object b, Object p, Object v);
                    public abstract boolean isReadOnly(ELContext c, Object b, Object p);
                    public abstract Class<?> getCommonPropertyType(ELContext c, Object b);
                    public <T> T convertToType(ELContext c, Object value, Class<T> targetType) { return null; }
                }
                """,
                "package jakarta.el; public abstract class Expression implements java.io.Serializable { }",
                "package jakarta.el; public abstract class ValueExpression extends Expression { public abstract Object getValue(ELContext c); }",
                "package jakarta.el; public abstract class MethodExpression extends Expression { public boolean isParametersProvided(){return false;} }",
                "package jakarta.el; public class StandardELContext extends ELContext { public StandardELContext(ExpressionFactory f){} }",
                "package jakarta.el; public class ImportHandler { public void importClass(String n){} public void importPackage(String n){} public void importStatic(String n){} public Class<?> resolveClass(String n){return null;} public Class<?> resolveStatic(String n){return null;} }",
                "package jakarta.el; public abstract class ExpressionFactory { public static ExpressionFactory newInstance(){return null;} public abstract ValueExpression createValueExpression(ELContext c,String e,Class<?> t); public abstract MethodExpression createMethodExpression(ELContext c,String e,Class<?> t,Class<?>[] p); }",
                "package jakarta.el; public class ELProcessor { public Object eval(String expression){return null;} }"
        };
    }
}
