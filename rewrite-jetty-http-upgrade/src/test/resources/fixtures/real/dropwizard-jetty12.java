import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;

class DropwizardJetty12Fixture {
    HttpFields.Mutable responseFields() {
        HttpFields.Mutable responseFields = HttpFields.build();
        responseFields.add("Testheader", "Testvalue1");
        responseFields.add("Testheader", "Testvalue2");
        return responseFields;
    }

    String path(HttpURI httpURI) {
        return httpURI.getPath();
    }
}
