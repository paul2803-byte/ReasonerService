package org.soya.consent;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

import java.util.HashMap;
import java.util.Map;

public class InstanceHandle {

    public static Map<String, Integer> idCount = new HashMap<>();

    public static Resource createInstance(String ns, String name, InstantType type) {
        if (type.equals(InstantType._DATASET)) {
            return ResourceFactory.createResource(ns + name);
        } else {
            return ResourceFactory.createResource(ns + name + type.name());
        }
    }

    public static Resource createInstance(String name) {
        return ResourceFactory.createResource(name);
    }

    public static Resource createInstance(String ns, String name, InstantType type, String suffix) {
        return ResourceFactory.createResource(ns + name + type.name() + "_" + suffix);
    }

    public static String createId(InstantType type) {
        idCount.merge(type.name(), 1, Integer::sum);
        return type.name() + "_" + idCount.get(type.name());
    }

    public static void cleanData() {
        idCount.clear();
    }

    public enum InstantType {
        _DATASET,
        _CONSENT,
        _CATEGORY,
        _EXPERIMENT,
        _USER
    }
}
