package org.soya.consent;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import jakarta.json.JsonArray;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Matching {

    public final static String BASE_FILE = "simple_consent.ttl";
    private static final Logger log = LoggerFactory.getLogger(Matching.class);
    private static final String PROPERTY_IRI = "https://soya.ownyourdata.eu/SimpleConsent/";

    public static ReasoningResult matchingString(FlexibleConsentHandler handle) {

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        try {
            // create an ontology from the base file
            InputStream libraryIS = Matching.class.getClassLoader().getResourceAsStream(BASE_FILE);
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(libraryIS);

            // add the d2a axioms to the ontology
            InputStream d2aIS = IOUtils.toInputStream(modelToString(handle.getD2aModel()), "UTF-8");
            ontology.addAxioms(manager.loadOntologyFromOntologyDocument(d2aIS).axioms());
            d2aIS.close();

            // add the d3a axioms to the ontology
            InputStream d3aIS = IOUtils.toInputStream(modelToString(handle.getD3aModel()), "UTF-8");
            ontology.addAxioms(manager.loadOntologyFromOntologyDocument(d3aIS).axioms());
            d3aIS.close();

            // create subclass axiom and check if it can be entailed in the ontolgy
            OWLClass dataControllerCls = df.getOWLClass(IRI.create(FlexibleConsentHandler.HANDLING));
            OWLClass dataSubjectCls = df.getOWLClass(IRI.create(FlexibleConsentHandler.CONSENT));
            OWLReasonerFactory rf = new ReasonerFactory();
            OWLReasoner r = rf.createReasoner(ontology);
            OWLAxiom axiom = df.getOWLSubClassOfAxiom(dataControllerCls, dataSubjectCls);
            boolean valid = r.isEntailed(axiom);

            // TODO: check for time

            // if it cannot be entailed call the explanation function
            if (!valid) {
                return new ReasoningResult(false, findMismatch(r, df, handle));
            }
            return new ReasoningResult(true);


        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ReasoningResult(false, new LinkedList<>(Collections.singletonList(e.getMessage())));
        }
    }

    private static List<String> findMismatch(OWLReasoner r, OWLDataFactory df, FlexibleConsentHandler handle) {

        List<String> messages = new LinkedList<>();
        for (String usage : handle.getD3aJson().keySet()) {
            JsonArray d3aValues = handle.getD3aJson().getJsonArray(usage);
            JsonArray d2aValues = handle.getD2aJson().getJsonArray(usage);
            List<String> invalidValues = new LinkedList<>();
            for (int i = 0; i < d3aValues.size(); i++) {
                boolean valid = false;
                for (int j = 0; j < d2aValues.size(); j++) {
                    valid = valid || isSuperProperty(r, df, d3aValues.getString(i), d2aValues.getString(i));
                }
                if (!valid) {
                    invalidValues.add(d3aValues.getString(i));
                }
            }
            invalidValues.forEach(x -> messages.add(String.format(
                    "For the usage class %s, the value %s is requested by the consumer but not covered by the provider", usage, x)));
        }
        return messages;
    }

    private static String modelToString(Model model) {
        StringWriter writer = new StringWriter();
        model.write(writer, Lang.TURTLE.getName());
        return writer.toString();
    }

    private static boolean isSuperProperty(OWLReasoner r, OWLDataFactory df, String d3a, String d2a) {
        String d3aIRI = PROPERTY_IRI + d3a;
        String d2aIRI = PROPERTY_IRI + d2a;
        OWLObjectProperty d3aProperty = df.getOWLObjectProperty(d3aIRI);
        OWLObjectProperty d2aProperty = df.getOWLObjectProperty(d2aIRI);
        OWLSubObjectPropertyOfAxiom subPropertyAxiom = df.getOWLSubObjectPropertyOfAxiom(d3aProperty, d2aProperty);
        return r.isEntailed(subPropertyAxiom);
    }

}
