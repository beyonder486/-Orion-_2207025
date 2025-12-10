package com.orion;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {

    // Java/C/C++/JavaScript keywords
    private static final String[] KEYWORDS = new String[] {
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "final", "finally", "float", "for", "goto", "if", "implements",
        "import", "instanceof", "int", "interface", "long", "native", "new", "package",
        "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient",
        "try", "void", "volatile", "while",
        // C/C++ specific
        "auto", "extern", "register", "signed", "sizeof", "struct", "typedef",
        "union", "unsigned", "include", "define", "ifdef", "ifndef", "endif",
        // JavaScript specific
        "let", "var", "function", "console", "null", "undefined", "typeof"
    };

    // Python keywords
    private static final String[] PYTHON_KEYWORDS = new String[] {
        "and", "as", "assert", "break", "class", "continue", "def", "del", "elif",
        "else", "except", "False", "finally", "for", "from", "global", "if", "import",
        "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise",
        "return", "True", "try", "while", "with", "yield", "async", "await", "print"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String PYTHON_KEYWORD_PATTERN = "\\b(" + String.join("|", PYTHON_KEYWORDS) + ")\\b";
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = ";";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";
    private static final String PYTHON_COMMENT_PATTERN = "#[^\n]*";
    private static final String NUMBER_PATTERN = "\\b\\d+\\.?\\d*\\b";

    private static Pattern JAVA_PATTERN;
    private static Pattern PYTHON_PATTERN;

    static {
        JAVA_PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
            + "|(?<PAREN>" + PAREN_PATTERN + ")"
            + "|(?<BRACE>" + BRACE_PATTERN + ")"
            + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
            + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
            + "|(?<STRING>" + STRING_PATTERN + ")"
            + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
            + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
        );

        PYTHON_PATTERN = Pattern.compile(
            "(?<KEYWORD>" + PYTHON_KEYWORD_PATTERN + ")"
            + "|(?<PAREN>" + PAREN_PATTERN + ")"
            + "|(?<BRACE>" + BRACE_PATTERN + ")"
            + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
            + "|(?<STRING>" + STRING_PATTERN + ")"
            + "|(?<COMMENT>" + PYTHON_COMMENT_PATTERN + ")"
            + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
        );
    }

    public static StyleSpans<Collection<String>> computeHighlighting(String text, String fileExtension) {
        Pattern pattern = getPatternForExtension(fileExtension);
        Matcher matcher = pattern.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while(matcher.find()) {
            String styleClass = null;
            
            if (matcher.group("KEYWORD") != null) styleClass = "keyword";
            else if (matcher.group("PAREN") != null) styleClass = "paren";
            else if (matcher.group("BRACE") != null) styleClass = "brace";
            else if (matcher.group("BRACKET") != null) styleClass = "bracket";
            else if (matcher.group("STRING") != null) styleClass = "string";
            else if (matcher.group("COMMENT") != null) styleClass = "comment";
            else if (matcher.group("NUMBER") != null) styleClass = "number";
            else {
                // Check for SEMICOLON only if it exists in the pattern
                try {
                    if (matcher.group("SEMICOLON") != null) styleClass = "semicolon";
                } catch (IllegalArgumentException e) {
                    // SEMICOLON group doesn't exist in this pattern (Python)
                }
            }

            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private static Pattern getPatternForExtension(String fileExtension) {
        if (fileExtension == null) return JAVA_PATTERN;
        
        if (fileExtension.endsWith(".py")) {
            return PYTHON_PATTERN;
        } else {
            return JAVA_PATTERN; // For .java, .c, .cpp, .js
        }
    }
}
