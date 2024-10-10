package org.soya.consent;

import java.util.LinkedList;
import java.util.List;

public class ReasoningResult {

    private boolean valid;
    private List<String> violations;

    public ReasoningResult(boolean valid, List<String> violations){
        this.valid = valid;
        this.violations = violations;
    }

    public ReasoningResult(boolean valid){
        this(valid, new LinkedList<>());
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getViolations() {
        return violations;
    }
}
