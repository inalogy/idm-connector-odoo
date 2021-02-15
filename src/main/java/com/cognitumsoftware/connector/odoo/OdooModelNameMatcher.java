package com.cognitumsoftware.connector.odoo;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Simple pattern matcher using a pattern from connector configuration. Supports comma-separated pattern list,
 * each pattern can have a wildcard * at the end for prefix matching. Whitespaces are trimmed. Example: Pattern
 * "example1*,example2 ,  example3" will match against "example1xyz", "example3" etc
 */
public class OdooModelNameMatcher {

    private List<String> patterns;
    private boolean alwaysMatchEmptyPatterns;

    public OdooModelNameMatcher(String patternList, boolean alwaysMatchEmptyPatterns) {
        this.patterns = Arrays.asList(StringUtils.defaultString(patternList).trim().split("\\s*,\\s*"));
        this.alwaysMatchEmptyPatterns = alwaysMatchEmptyPatterns;
    }

    /**
     * @return true if model name matches the configured pattern
     */
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
