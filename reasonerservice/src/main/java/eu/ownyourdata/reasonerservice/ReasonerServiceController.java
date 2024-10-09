package eu.ownyourdata.reasonerservice;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.soya.consent.FlexibleConsentHandler;
import org.soya.consent.Matching;
import org.soya.consent.UnregisteredTermException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;


@RestController
public class ReasonerServiceController {

    private final static String VERSION = "1.0.0";

    // TODO: think about the general interface (really two yaml strings in REST call)

    @PutMapping("/api/match/")
    public ResponseEntity<JsonObject> change(@RequestBody String reasoning) {

        // convert string input to JSON objects
        JsonObject body = Json.createReader(new StringReader(reasoning)).readObject();
        JsonObject d2aConsent = body.getJsonObject("d2a-consent");
        JsonObject d3aConsent = body.getJsonObject("d3a-consent");
        if(d2aConsent == null || d3aConsent == null) {
            return new ResponseEntity<>(
                    createResponse(false, "Either d2a-consent or d3a-consent are not set"),
                    HttpStatus.BAD_REQUEST
            );
        }

        // create RDF models
        Model baseModel = ModelFactory.createDefaultModel();
        InputStream baseFileIS = ReasonerServiceController.class.getClassLoader().getResourceAsStream(Matching.BASE_FILE);
        RDFDataMgr.read(baseModel, baseFileIS, Lang.TURTLE);
        FlexibleConsentHandler handle = new FlexibleConsentHandler(baseModel);
        Resource d2aConsentResource = baseModel.createResource(FlexibleConsentHandler.CONSENT);
        Resource d3aConsentResource = baseModel.createResource(FlexibleConsentHandler.HANDLING);

        try {
            Model d2aConsentModel = handle.getConsent(d2aConsentResource, d2aConsent);
            Model d3aConsentModel = handle.getConsent(d3aConsentResource, d3aConsent);
            boolean matching = Matching.matchingString(
                    modelToString(d2aConsentModel),
                    modelToString(d3aConsentModel),
                    FlexibleConsentHandler.CONSENT,
                    FlexibleConsentHandler.HANDLING);

            // return response
            return new ResponseEntity<>(
                    createResponse(matching, null),
                    HttpStatus.ACCEPTED
            );
        } catch (UnregisteredTermException e) {
            return new ResponseEntity<>(
                    createResponse(false, e.getMessage()),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private static String modelToString(Model model) {
        StringWriter writer = new StringWriter();
        model.write(writer, Lang.TURTLE.getName());
        return writer.toString();
    }

    private static JsonObject createResponse(boolean match, String message) {
        String response = message != null ? (String.format("""                                                  
                {
                  "version": "%s",
                  "match": %b,
                  "messages": "%s"
                }
                """, VERSION, match, message)) :
                (String.format("""                                                  
                {
                  "version": "%s",
                  "match": %b
                }
                """, VERSION, match));
        return Json.createReader(new StringReader(response)).readObject();
    }
}