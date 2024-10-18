package org.soya.consent;

import java.io.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import jakarta.json.JsonArray;
import org.apache.commons.io.IOUtils;
import org.apache.jena.ontology.DatatypeProperty;
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

    // TODO when data prop works include in reasoning
    private static final Logger log = LoggerFactory.getLogger(Matching.class);

    public static ReasoningResult matchingString(Consent handle) {

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        try {
            // create an ontology from the handle model
            OWLOntology ontology = manager.createOntology();
            InputStream axiomsIS = IOUtils.toInputStream(modelToString(handle.baseModel()), "UTF-8");
            ontology.addAxioms(manager.loadOntologyFromOntologyDocument(axiomsIS).axioms());
            axiomsIS.close();

            // add the d2a axioms to the ontology
            InputStream d2aIS = IOUtils.toInputStream(modelToString(handle.d2aModel()), "UTF-8");
            ontology.addAxioms(manager.loadOntologyFromOntologyDocument(d2aIS).axioms());
            d2aIS.close();

            // add the d3a axioms to the ontology
            InputStream d3aIS = IOUtils.toInputStream(modelToString(handle.d3aModel()), "UTF-8");
            ontology.addAxioms(manager.loadOntologyFromOntologyDocument(d3aIS).axioms());
            d3aIS.close();

            // create subclass axiom and check if it can be entailed in the ontology
            OWLClass dataControllerCls = df.getOWLClass(IRI.create(Consent.HANDLING));
            OWLClass dataSubjectCls = df.getOWLClass(IRI.create(Consent.CONSENT));
            OWLReasonerFactory rf = new ReasonerFactory();
            OWLReasoner r = rf.createReasoner(ontology);
            OWLAxiom axiom = df.getOWLSubClassOfAxiom(dataControllerCls, dataSubjectCls);
            boolean valid = r.isEntailed(axiom);

            List<String> violations = new LinkedList<>();

            if (!valid) {
                violations.addAll(findMismatch(r, df, handle));
            }
            return new ReasoningResult(valid, violations);


        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new ReasoningResult(false, new LinkedList<>(Collections.singletonList(e.getMessage())));
        }
    }

    private static List<String> findMismatch(OWLReasoner r, OWLDataFactory df, Consent handle) {

        List<String> messages = new LinkedList<>();
        handle.baseModel().listObjectProperties().forEach(cp -> {
            JsonArray d3aValues = handle.d3aJson().getJsonArray(cp.getLocalName());
            JsonArray d2aValues = handle.d2aJson().getJsonArray(cp.getLocalName());
            List<String> invalidValues = new LinkedList<>();
            for (int i = 0; i < d3aValues.size(); i++) {
                boolean valid = false;
                for (int j = 0; j < d2aValues.size(); j++) {
                    valid = valid || isSuperProperty(r, df, d3aValues.getString(i), d2aValues.getString(j), handle.baseURL());
                }
                if (!valid) {
                    invalidValues.add(d3aValues.getString(i));
                }
            }
            invalidValues.forEach(x -> messages.add(String.format(
                    "For the usage class %s, the value %s is requested by the consumer but not covered by the provider"
                    , cp.getLocalName(), x)));
        });
        handle.baseModel().listDatatypeProperties().forEach(dp -> {
            int d3aValue = handle.d3aJson().getInt(dp.getLocalName());
            int d2aValue = handle.d2aJson().getInt(dp.getLocalName());
            // Maybe in the future include possibility that the d3a must be smaller
            if(d3aValue > d2aValue) {
                messages.add(String.format(
                        "The d2a value for %s must be greater than the one for d3a", dp.getLocalName()
                ));
            }
        });
        return messages;
    }

    private static String modelToString(Model model) {
        StringWriter writer = new StringWriter();
        model.write(writer, Lang.TURTLE.getName());
        return writer.toString();
    }

    private static boolean isSuperProperty(OWLReasoner r, OWLDataFactory df, String d3a, String d2a, String URL) {
        String d3aIRI = URL + d3a;
        String d2aIRI = URL + d2a;
        OWLObjectProperty d3aProperty = df.getOWLObjectProperty(d3aIRI);
        OWLObjectProperty d2aProperty = df.getOWLObjectProperty(d2aIRI);
        OWLSubObjectPropertyOfAxiom subPropertyAxiom = df.getOWLSubObjectPropertyOfAxiom(d3aProperty, d2aProperty);
        return r.isEntailed(subPropertyAxiom);
    }
}
