package com.huawei.clouds.openrewrite.springsecuritycore;

import org.openrewrite.Cursor;
import org.openrewrite.xml.tree.Xml;

final class SpringSecurityCoreXmlSupport {
    static final String SECURITY_NAMESPACE =
            "http://www.springframework.org/schema/security";

    private SpringSecurityCoreXmlSupport() {
    }

    static boolean securityElement(Cursor cursor, Xml.Tag tag) {
        String name = tag.getName();
        int separator = name.indexOf(':');
        String prefix = separator < 0 ? "" : name.substring(0, separator);
        String declaration = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
        for (Cursor current = cursor; current != null;
             current = current.getParentTreeCursor()) {
            if (current.getValue() instanceof Xml.Tag scope) {
                for (Xml.Attribute attribute : scope.getAttributes()) {
                    if (declaration.equals(attribute.getKeyAsString())) {
                        return SECURITY_NAMESPACE.equals(attribute.getValueAsString());
                    }
                }
            }
            if (current.getValue() instanceof Xml.Document) break;
        }
        return false;
    }
}
