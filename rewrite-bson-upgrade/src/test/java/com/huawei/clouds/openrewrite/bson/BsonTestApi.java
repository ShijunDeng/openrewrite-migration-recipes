package com.huawei.clouds.openrewrite.bson;

final class BsonTestApi {
    private BsonTestApi() {
    }

    static String[] legacySources() {
        return new String[]{
                """
                package org.bson.types;
                public final class ObjectId implements java.io.Serializable {
                    public ObjectId(){} public ObjectId(int time,int machine,short process,int counter){}
                    public static ObjectId createFromLegacyFormat(int time,int machine,int inc){return null;}
                    public int getTimeSecond(){return 0;} public long getTime(){return 0L;}
                    public String toStringMongod(){return null;} public int getMachineIdentifier(){return 0;}
                    public short getProcessIdentifier(){return 0;} public int getCounter(){return 0;}
                    public static int getCurrentCounter(){return 0;}
                }
                """,
                "package org.bson.json; public enum JsonMode { STRICT, SHELL, EXTENDED, RELAXED }",
                """
                package org.bson.json;
                public final class JsonWriterSettings {
                    public JsonWriterSettings(){} public JsonWriterSettings(JsonMode m){}
                    public JsonWriterSettings(boolean i){} public JsonWriterSettings(JsonMode m,boolean i){}
                    public JsonWriterSettings(JsonMode m,String i){} public JsonWriterSettings(JsonMode m,String i,String n){}
                }
                """,
                "package org.bson.codecs.record.annotations; public @interface BsonId {}",
                "package org.bson.codecs.record.annotations; public @interface BsonProperty { String value() default \"\"; }",
                "package org.bson.codecs.record.annotations; public @interface BsonRepresentation { int value() default 0; }",
                "package org.bson.codecs.pojo.annotations; public @interface BsonId {}",
                "package org.bson.codecs.pojo.annotations; public @interface BsonProperty { String value() default \"\"; }",
                "package org.bson.codecs.pojo.annotations; public @interface BsonRepresentation { int value() default 0; }",
                "package org.bson; public enum UuidRepresentation { UNSPECIFIED, STANDARD, JAVA_LEGACY, C_SHARP_LEGACY, PYTHON_LEGACY }",
                "package org.bson.codecs; public class UuidCodec { public UuidCodec(){} public UuidCodec(org.bson.UuidRepresentation r){} }",
                "package org.bson; public interface BsonReader { void mark(); void reset(); BsonReaderMark getMark(); }",
                "package org.bson; public interface BsonReaderMark { void reset(); }",
                "package org.bson; public interface BsonWriter { void flush(); }",
                "package org.bson; public class Document { public String toJson(){return null;} public String toJson(org.bson.json.JsonWriterSettings s){return null;} }",
                "package org.bson; public class BsonDocument { public String toJson(){return null;} }",
                "package org.bson; public class RawBsonDocument { public String toJson(){return null;} }",
                "package org.bson; public class BsonNumber {}",
                "package org.bson; public class BsonDecimal128 { public boolean isNumber(){return false;} public BsonNumber asNumber(){return null;} }",
                "package org.bson; public class BSON { public static byte[] encode(Object o){return null;} public static void addEncodingHook(Class<?> c, Transformer t){} }",
                "package org.bson; public interface Transformer { Object transform(Object v); }",
                "package org.bson.io; public final class Bits { public static int readInt(byte[] b){return 0;} }",
                "package org.bson.util; public class CopyOnWriteMap<K,V> {}",
                "package org.bson.codecs; public interface Codec<T> {}",
                "package org.bson.codecs; public class MapCodec implements Codec<java.util.Map<String,Object>> { public MapCodec(){} }",
                "package org.bson.codecs; public class IterableCodec implements Codec<Iterable> { public IterableCodec(Object a,Object b){} }",
                "package org.bson.codecs; public interface Parameterizable { Codec<?> parameterize(Object t, Object r); }"
        };
    }

    static String[] targetSources() {
        return new String[]{
                "package org.bson.types; public final class ObjectId { public int getTimestamp(){return 0;} public java.util.Date getDate(){return null;} public String toHexString(){return null;} }",
                "package org.bson.json; public enum JsonMode { STRICT, SHELL, EXTENDED, RELAXED }",
                """
                package org.bson.json;
                public final class JsonWriterSettings {
                    public static Builder builder(){return null;}
                    public static final class Builder { public Builder outputMode(JsonMode m){return this;}
                    public Builder indent(boolean b){return this;} public Builder indentCharacters(String s){return this;}
                    public Builder newLineCharacters(String s){return this;} public JsonWriterSettings build(){return null;} }
                }
                """,
                "package org.bson.codecs.pojo.annotations; public @interface BsonId {}",
                "package org.bson.codecs.pojo.annotations; public @interface BsonProperty { String value() default \"\"; }",
                "package org.bson.codecs.pojo.annotations; public @interface BsonRepresentation { int value() default 0; }"
        };
    }
}
