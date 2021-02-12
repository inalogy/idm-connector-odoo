package com.cognitumsoftware.connector.odoo;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

public class OdooModelNameMatcher {

    private List<String> patterns;
    private boolean alwaysMatchEmptyPatterns;

    public OdooModelNameMatcher(String patternList, boolean alwaysMatchEmptyPatterns) {
        this.patterns = Arrays.asList(StringUtils.defaultString(patternList).trim().split("\\s*,\\s*"));
        this.alwaysMatchEmptyPatterns = alwaysMatchEmptyPatterns;
    }

    public boolean matches(String modelName) {
        if (patterns.isEmpty()) {
            return alwaysMatchEmptyPatterns;
        }

        for (String pattern : patterns) {
            if (pattern.equals(modelName)
                    || (pattern.endsWith("*") && modelName.startsWith(pattern.substring(0, pattern.length() - 1)))) {
                return true;
            }
        }

        return false;
    }

}
