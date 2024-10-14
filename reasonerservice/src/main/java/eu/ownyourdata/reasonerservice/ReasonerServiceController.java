package eu.ownyourdata.reasonerservice;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.soya.consent.FlexibleConsentHandler;
import org.soya.consent.Matching;
import org.soya.consent.ReasoningResult;
import org.soya.consent.UnregisteredTermException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedList;


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
            OntModel ontology = fetchOntology(ontologyURL);
            FlexibleConsentHandler handle = new FlexibleConsentHandler(d2aConsent, d3aConsent, ontology);
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

    private OntModel fetchOntology(String url) throws JsonLdError {
        OntModel ontology = ModelFactory.createOntologyModel();
        JsonLd.toRdf(url).get().toList().forEach(axiom -> {
            Resource subject = ontology.createResource(axiom.getSubject().getValue());
            Property predicate = ontology.createProperty(axiom.getPredicate().getValue());
            Resource object = ontology.createResource(axiom.getObject().getValue());
            ontology.add(ontology.createStatement(subject, predicate, object));
        });
        return ontology;
    }
}