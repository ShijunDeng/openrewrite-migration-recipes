import org.eclipse.jetty.http.HttpFields;

class Jetty9HttpFieldsFixture {
    HttpFields headers() {
        HttpFields header = new HttpFields();
        header.add("name0", "value0");
        header.add("name1", "value1");
        return header;
    }
}
