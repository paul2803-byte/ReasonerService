package eu.ownyourdata.reasonerservice;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.JsonDocument;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.soya.consent.Consent;
import org.soya.consent.ConsentFactory;
import org.soya.consent.Matching;
import org.soya.consent.ReasoningResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


@RestController
public class ReasonerServiceController {

    private final static String VERSION = "1.0.0";

    @GetMapping("/version")
    public ResponseEntity<String> get() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("version", VERSION);
        builder.add("service", "reasoner");
        return new ResponseEntity<>(
                builder.build().toString(),
                HttpStatus.ACCEPTED
        );
    }

    @PutMapping("/api/match")
    public ResponseEntity<String> change(@RequestBody String reasoning) {

        System.out.println("Received request. Body: \n" + reasoning);

        // convert string input to JSON objects
        JsonObject body = Json.createReader(new StringReader(reasoning)).readObject();
        JsonObject d2aConsent = body.getJsonObject("d2a-consent");
        JsonObject d3aConsent = body.getJsonObject("d3a-consent");
        String ontologyURL = body.getString("ontology");

        // check if both consents are set
        if(d2aConsent == null || d3aConsent == null || ontologyURL == null) {
            return new ResponseEntity<>(
                    createResponse(new ReasoningResult(false,"Either d2a-consent, d3a-consent or the ontology URL are not set in the request body.")),
                    HttpStatus.BAD_REQUEST);
        }

        try {
            String jsonLDAsString = fetchContent(ontologyURL);
            OntModel ontology = createOntology(jsonLDAsString);
            String baseURL = getBaseURL(jsonLDAsString);
            ConsentFactory consentFactory = new ConsentFactory();
            Consent handle = consentFactory.createConsent(d2aConsent, d3aConsent, ontology, baseURL);
            ReasoningResult result = Matching.matchingString(handle);
            return new ResponseEntity<>(
                    createResponse(result),
                    HttpStatus.ACCEPTED);
        } catch (Exception e) {
            return new ResponseEntity<>(
                    createResponse(new ReasoningResult(false,e.getMessage())),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static String createResponse(ReasoningResult result) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("version", VERSION);
        builder.add("valid", result.isValid());
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        result.getViolations().forEach(arrayBuilder::add);
        builder.add("messages", arrayBuilder);
        return builder.build().toString();
    }

    private String fetchContent(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String getBaseURL(String jsonLDString) {
        JsonObject body = Json.createReader(new StringReader(jsonLDString)).readObject();
        JsonObject context = body.getJsonObject("@context");
        return context.getString("@base");
    }

    // TODO change to the framework sent by Fajar --> currently not working
    // TODO check with Gabriel and Fajar how to handle set types
    private OntModel createOntology(String jsonLDString) throws JsonLdError {

        /*Model model = ModelFactory.createDefaultModel();
        InputStream modelIS = new ByteArrayInputStream(jsonLDString.getBytes(StandardCharsets.UTF_8));
        RDFDataMgr.read(model, "simple_consent.jsonld", Lang.JSONLD);
        model.write(System.out, "RDF/XML");
        */

        OntModel ontology = ModelFactory.createOntologyModel();
        JsonLd.toRdf(JsonDocument.of(new ByteArrayInputStream(jsonLDString.getBytes()))).get().toList().forEach(axiom -> {
            Resource subject = ontology.createResource(axiom.getSubject().getValue());
            Property predicate = ontology.createProperty(axiom.getPredicate().getValue());
            Resource object = ontology.createResource(axiom.getObject().getValue());
            if(!subject.getURI().equals("set") && !predicate.getURI().equals("set") && !object.getURI().equals("set")){
                ontology.add(ontology.createStatement(subject, predicate, object));
            }
        });
        return ontology;
    }
}