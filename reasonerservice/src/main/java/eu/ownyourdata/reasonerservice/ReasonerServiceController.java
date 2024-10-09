package eu.ownyourdata.reasonerservice;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.soya.consent.FlexibleConsentHandler;
import org.soya.consent.Matching;
import org.soya.consent.ReasoningResult;
import org.soya.consent.UnregisteredTermException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedList;


@RestController
public class ReasonerServiceController {

    private final static String VERSION = "1.0.0";

    // TODO: think about the general interface (really two yaml strings in REST call)

    @PutMapping("/api/match/")
    public ResponseEntity<JsonObject> change(@RequestBody String reasoning) throws UnregisteredTermException {

        // convert string input to JSON objects
        JsonObject body = Json.createReader(new StringReader(reasoning)).readObject();
        JsonObject d2aConsent = body.getJsonObject("d2a-consent");
        JsonObject d3aConsent = body.getJsonObject("d3a-consent");
        if(d2aConsent == null || d3aConsent == null) {
            return new ResponseEntity<>(
                    createResponse(new ReasoningResult(false, new LinkedList<>(Collections.singletonList("Either d2a-consent or d3a-consent are not set")))),
                    HttpStatus.BAD_REQUEST
            );
        }

        FlexibleConsentHandler handle = new FlexibleConsentHandler(d2aConsent, d3aConsent);

        try {
            ReasoningResult result = Matching.matchingString(handle);

            return new ResponseEntity<>(
                    createResponse(result),
                    HttpStatus.ACCEPTED
            );
        } catch (Exception e) {
            return new ResponseEntity<>(
                    createResponse(new ReasoningResult(false, new LinkedList<>(Collections.singletonList(e.getMessage())))),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private static JsonObject createResponse(ReasoningResult result) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("version", VERSION);
        builder.add("valid", result.isValid());
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        result.getViolations().forEach(arrayBuilder::add);
        builder.add("messages", arrayBuilder);
        return builder.build();
    }

    private static String modelToString(Model model) {
        StringWriter writer = new StringWriter();
        model.write(writer, Lang.TURTLE.getName());
        return writer.toString();
    }
}