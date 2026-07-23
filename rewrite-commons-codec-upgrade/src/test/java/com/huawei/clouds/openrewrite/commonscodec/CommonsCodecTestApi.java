package com.huawei.clouds.openrewrite.commonscodec;

final class CommonsCodecTestApi {
    private CommonsCodecTestApi() { }

    static String[] sources() {
        return new String[]{
                "package org.apache.commons.codec; public enum CodecPolicy { STRICT, LENIENT }",
                "package org.apache.commons.codec; public class EncoderException extends Exception { public EncoderException(){} }",
                """
                package org.apache.commons.codec;
                public class Charsets {
                    public static final java.nio.charset.Charset ISO_8859_1 = java.nio.charset.StandardCharsets.ISO_8859_1;
                    public static final java.nio.charset.Charset US_ASCII = java.nio.charset.StandardCharsets.US_ASCII;
                    public static final java.nio.charset.Charset UTF_16 = java.nio.charset.StandardCharsets.UTF_16;
                    public static final java.nio.charset.Charset UTF_16BE = java.nio.charset.StandardCharsets.UTF_16BE;
                    public static final java.nio.charset.Charset UTF_16LE = java.nio.charset.StandardCharsets.UTF_16LE;
                    public static final java.nio.charset.Charset UTF_8 = java.nio.charset.StandardCharsets.UTF_8;
                }
                """,
                """
                package org.apache.commons.codec.digest;
                public class DigestUtils {
                    public static java.security.MessageDigest getShaDigest(){ return null; }
                    public static java.security.MessageDigest getSha1Digest(){ return null; }
                    public static byte[] sha(byte[] value){ return value; }
                    public static byte[] sha(String value){ return null; }
                    public static byte[] sha(java.io.InputStream value) throws java.io.IOException { return null; }
                    public static byte[] sha1(byte[] value){ return value; }
                    public static byte[] sha1(String value){ return null; }
                    public static byte[] sha1(java.io.InputStream value) throws java.io.IOException { return null; }
                    public static String shaHex(byte[] value){ return null; }
                    public static String shaHex(String value){ return null; }
                    public static String shaHex(java.io.InputStream value) throws java.io.IOException { return null; }
                    public static String sha1Hex(byte[] value){ return null; }
                    public static String sha1Hex(String value){ return null; }
                    public static String sha1Hex(java.io.InputStream value) throws java.io.IOException { return null; }
                }
                """,
                """
                package org.apache.commons.codec.binary;
                public class Base64 {
                    public static final int MIME_CHUNK_SIZE = 76;
                    public Base64(){}
                    public Base64(boolean urlSafe){}
                    public Base64(int lineLength, byte[] lineSeparator){}
                    public Base64(int lineLength, byte[] lineSeparator, boolean urlSafe){}
                    public Base64(int lineLength, byte[] lineSeparator, boolean urlSafe, org.apache.commons.codec.CodecPolicy policy){}
                    public static boolean isArrayByteBase64(byte[] value){ return true; }
                    public static boolean isBase64(byte[] value){ return true; }
                    public static byte[] decodeBase64(byte[] value){ return value; }
                    public static byte[] decodeBase64(String value){ return null; }
                    public static byte[] encodeBase64(byte[] value){ return value; }
                    public static byte[] encodeBase64(byte[] value, boolean chunked){ return value; }
                    public static byte[] encodeBase64Chunked(byte[] value){ return value; }
                    public static String encodeBase64String(byte[] value){ return null; }
                    public static byte[] encodeBase64URLSafe(byte[] value){ return value; }
                    public static String encodeBase64URLSafeString(byte[] value){ return null; }
                    public byte[] encode(byte[] value){ return value; }
                    public byte[] decode(byte[] value){ return value; }
                    public static Builder builder(){ return new Builder(); }
                    public static class Builder {
                        public Builder setDecodingPolicy(org.apache.commons.codec.CodecPolicy policy){ return this; }
                        public Builder setCodecPolicy(org.apache.commons.codec.CodecPolicy policy){ return this; }
                        public Base64 get(){ return new Base64(); }
                    }
                }
                """,
                """
                package org.apache.commons.codec.binary;
                public class Base32 {
                    public Base32(){}
                    public Base32(int lineLength, byte[] lineSeparator){}
                    public Base32(int lineLength, byte[] lineSeparator, boolean useHex, byte padding, org.apache.commons.codec.CodecPolicy policy){}
                    public static Builder builder(){ return new Builder(); }
                    public static class Builder {
                        public Builder setDecodingPolicy(org.apache.commons.codec.CodecPolicy policy){ return this; }
                        public Base32 get(){ return new Base32(); }
                    }
                }
                """,
                "package org.apache.commons.codec.binary; public class Base64InputStream { public Base64InputStream(java.io.InputStream in){} }",
                "package org.apache.commons.codec.binary; public class Base64OutputStream { public Base64OutputStream(java.io.OutputStream out){} }",
                """
                package org.apache.commons.codec.binary;
                public class Base16 { public byte[] decode(byte[] value){ return value; } }
                """,
                """
                package org.apache.commons.codec.binary;
                public class Hex {
                    public static char[] encodeHex(java.nio.ByteBuffer value){ return null; }
                    public static byte[] decodeHex(java.nio.ByteBuffer value){ return null; }
                }
                """,
                """
                package org.apache.commons.codec.digest;
                public class MurmurHash3 {
                    public static int hash32(byte[] data){ return 0; }
                    public static long hash64(byte[] data){ return 0; }
                    public static long[] hash128(byte[] data){ return null; }
                    public static int hash32x86(byte[] data){ return 0; }
                    public static class IncrementalHash32 { public void start(int seed){} public void add(byte[] data,int offset,int length){} public int end(){return 0;} }
                    public static class IncrementalHash32x86 { public void start(int seed){} public void add(byte[] data,int offset,int length){} public int end(){return 0;} }
                }
                """,
                "package org.apache.commons.codec.digest; public class Md5Crypt { public static String md5Crypt(byte[] data,String salt){return null;} public static String apr1Crypt(byte[] data,String salt){return null;} }",
                "package org.apache.commons.codec.language; public class ColognePhonetic { public String colognePhonetic(String s){return s;} public String encode(String s){return s;} }",
                "package org.apache.commons.codec.language; public class Metaphone { public String metaphone(String s){return s;} public String encode(String s){return s;} }",
                "package org.apache.commons.codec.language; public class DoubleMetaphone { public String doubleMetaphone(String s){return s;} public String encode(String s){return s;} }",
                "package org.apache.commons.codec.language; public class DaitchMokotoffSoundex { public String soundex(String s){return s;} public String encode(String s){return s;} }",
                "package org.apache.commons.codec.language; public class MatchRatingApproachEncoder { public String encode(String s){return s;} }",
                "package org.apache.commons.codec.language; public class RefinedSoundex { public String soundex(String s){return s;} public String encode(String s){return s;} }",
                "package org.apache.commons.codec.net; public class BCodec { public String encode(String s,String charset) throws org.apache.commons.codec.EncoderException{return s;} }",
                "package org.apache.commons.codec.net; public class QCodec { public String encode(String s,String charset) throws org.apache.commons.codec.EncoderException{return s;} }"
        };
    }
}
