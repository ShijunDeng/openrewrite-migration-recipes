import org.eclipse.jetty.http.HttpURI;

class Jetty9HttpUriFixture {
    String decodedPath(String input) {
        HttpURI uri = new HttpURI(input);
        uri.setPath("/foo/bar");
        return input;
    }
}
