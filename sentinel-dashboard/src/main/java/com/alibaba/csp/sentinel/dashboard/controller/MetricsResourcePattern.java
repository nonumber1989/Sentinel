package com.alibaba.csp.sentinel.dashboard.controller;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

//TODO need Dedbug more
public class MetricsResourcePattern {
    public static PathMatcher pathMatcher = new AntPathMatcher();

    private static final boolean useSuffixPatternMatch = true;

    private static final boolean useTrailingSlashMatch = false;

    private static final List<String> fileExtensions = Arrays.asList("json");

    public static boolean isPatternMatch(String lookupPath, Set<String> patterns) {
        boolean patternMatch = false;
        List<String> matches = getMatchingPatterns(lookupPath, patterns);
        if (!matches.isEmpty() && !matches.get(0).equals(lookupPath)) {
            patternMatch = true;
        }
        return patternMatch;
    }

    public static List<String> getMatchingPatterns(String lookupPath, Set<String> patterns) {
        List<String> matches = new ArrayList<>();
        for (String pattern : patterns) {
            String match = getMatchingPattern(pattern, lookupPath);
            if (match != null) {
                matches.add(match);
            }
        }
        if (matches.size() > 1) {
            matches.sort(pathMatcher.getPatternComparator(lookupPath));
        }
        return matches;
    }

    private static String getMatchingPattern(String pattern, String lookupPath) {
        if (pattern.equals(lookupPath)) {
            return pattern;
        }
        if (useSuffixPatternMatch) {
            if (!fileExtensions.isEmpty() && lookupPath.indexOf('.') != -1) {
                for (String extension : fileExtensions) {
                    if (pathMatcher.match(pattern + extension, lookupPath)) {
                        return pattern + extension;
                    }
                }
            } else {
                boolean hasSuffix = pattern.indexOf('.') != -1;
                if (!hasSuffix && pathMatcher.match(pattern + ".*", lookupPath)) {
                    return pattern + ".*";
                }
            }
        }
        if (pathMatcher.match(pattern, lookupPath)) {
            return pattern;
        }
        if (useTrailingSlashMatch) {
            if (!pattern.endsWith("/") && pathMatcher.match(pattern + "/", lookupPath)) {
                return pattern + "/";
            }
        }
        return null;
    }
}
