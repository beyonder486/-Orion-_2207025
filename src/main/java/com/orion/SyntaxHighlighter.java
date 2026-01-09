package com.orion;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighter {

    // Control flow keywords (if, else, for, while, etc.)
    private static final String[] CONTROL_KEYWORDS = new String[] {
        "if", "else", "elif", "for", "while", "do", "switch", "case", "default",
        "break", "continue", "return", "goto", "try", "catch", "finally", "throw", "throws",
        "assert", "yield", "await", "async"
    };
    
    // Type keywords (int, void, class, etc.)
    private static final String[] TYPE_KEYWORDS = new String[] {
        "int", "long", "short", "byte", "char", "float", "double", "boolean", "void",
        "class", "interface", "enum", "struct", "union", "typedef",
        "bool", "bytes", "str", "dict", "list", "tuple", "set", "frozenset"
    };
    
    // Storage modifiers (public, private, static, etc.)
    private static final String[] STORAGE_KEYWORDS = new String[] {
        "public", "private", "protected", "static", "final", "abstract", "synchronized",
        "volatile", "transient", "native", "strictfp", "const",
        "auto", "register", "extern", "signed", "unsigned",
        "var", "let", "function", "def"
    };
    
    // Other keywords (import, package, extends, etc.)
    private static final String[] OTHER_KEYWORDS = new String[] {
        "import", "from", "package", "as", "with", "in", "is", "instanceof",
        "new", "extends", "implements", "super", "this",
        "include", "define", "ifdef", "ifndef", "endif", "typeof",
        "and", "or", "not", "lambda", "del", "global", "nonlocal", "pass", "raise", "except"
    };
    
    // Language constants (True, False, None, null, undefined)
    private static final String[] CONSTANTS = new String[] {
        "True", "False", "None", "null", "undefined", "true", "false", "NaN", "Infinity"
    };

    // Python keywords (for backward compatibility)
    private static final String[] PYTHON_KEYWORDS = new String[] {
        "and", "as", "assert", "break", "class", "continue", "def", "del", "elif",
        "else", "except", "False", "finally", "for", "from", "global", "if", "import",
        "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass", "raise",
        "return", "True", "try", "while", "with", "yield", "async", "await", "print"
    };

    // Built-in functions for Python
    private static final String[] PYTHON_BUILTINS = new String[] {
        "abs", "all", "any", "bin", "bool", "bytes", "callable", "chr", "classmethod",
        "compile", "complex", "delattr", "dict", "dir", "divmod", "enumerate", "eval",
        "exec", "filter", "float", "format", "frozenset", "getattr", "globals", "hasattr",
        "hash", "help", "hex", "id", "input", "int", "isinstance", "issubclass", "iter",
        "len", "list", "locals", "map", "max", "memoryview", "min", "next", "object",
        "oct", "open", "ord", "pow", "property", "range", "repr", "reversed", "round",
        "set", "setattr", "slice", "sorted", "staticmethod", "str", "sum", "super",
        "tuple", "type", "vars", "zip", "print"
    };

    // Built-in functions for Java/JavaScript
    private static final String[] JAVA_BUILTINS = new String[] {
        "System", "String", "Integer", "Double", "Boolean", "Character", "Math",
        "Arrays", "List", "ArrayList", "HashMap", "HashSet", "Object", "Exception",
        "console", "window", "document", "alert", "prompt", "confirm", "parseInt",
        "parseFloat", "isNaN", "isFinite", "setTimeout", "setInterval", "JSON"
    };

    private static final String CONTROL_PATTERN = "\\b(" + String.join("|", CONTROL_KEYWORDS) + ")\\b";
    private static final String TYPE_PATTERN = "\\b(" + String.join("|", TYPE_KEYWORDS) + ")\\b";
    private static final String STORAGE_PATTERN = "\\b(" + String.join("|", STORAGE_KEYWORDS) + ")\\b";
    private static final String OTHER_KEYWORD_PATTERN = "\\b(" + String.join("|", OTHER_KEYWORDS) + ")\\b";
    private static final String CONSTANT_PATTERN = "\\b(" + String.join("|", CONSTANTS) + ")\\b";
    private static final String PYTHON_KEYWORD_PATTERN = "\\b(" + String.join("|", PYTHON_KEYWORDS) + ")\\b";
    private static final String PYTHON_BUILTIN_PATTERN = "\\b(" + String.join("|", PYTHON_BUILTINS) + ")\\b";
    private static final String JAVA_BUILTIN_PATTERN = "\\b(" + String.join("|", JAVA_BUILTINS) + ")\\b";
    
    // Function/method definitions and calls
    private static final String FUNCTION_PATTERN = "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()";
    private static final String CLASS_PATTERN = "\\b([A-Z][a-zA-Z0-9_]*)\\b";
    
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = ";";
    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";
    private static final String PYTHON_COMMENT_PATTERN = "#[^\n]*";
    private static final String NUMBER_PATTERN = "\\b\\d+\\.?\\d*\\b";
    private static final String OPERATOR_PATTERN = "[+\\-*/%=<>!&|^~]";

    private static Pattern JAVA_PATTERN;
    private static Pattern PYTHON_PATTERN;

    static {
        JAVA_PATTERN = Pattern.compile(
            "(?<COMMENT>" + COMMENT_PATTERN + ")"
            + "|(?<STRING>" + STRING_PATTERN + ")"
            + "|(?<CONSTANT>" + CONSTANT_PATTERN + ")"
            + "|(?<CONTROL>" + CONTROL_PATTERN + ")"
            + "|(?<TYPE>" + TYPE_PATTERN + ")"
            + "|(?<STORAGE>" + STORAGE_PATTERN + ")"
            + "|(?<OTHERKW>" + OTHER_KEYWORD_PATTERN + ")"
            + "|(?<BUILTIN>" + JAVA_BUILTIN_PATTERN + ")"
            + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
            + "|(?<FUNCTION>" + FUNCTION_PATTERN + ")"
            + "|(?<CLASS>" + CLASS_PATTERN + ")"
            + "|(?<OPERATOR>" + OPERATOR_PATTERN + ")"
            + "|(?<PAREN>" + PAREN_PATTERN + ")"
            + "|(?<BRACE>" + BRACE_PATTERN + ")"
            + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
            + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
        );

        PYTHON_PATTERN = Pattern.compile(
            "(?<COMMENT>" + PYTHON_COMMENT_PATTERN + ")"
            + "|(?<STRING>" + STRING_PATTERN + ")"
            + "|(?<CONSTANT>" + CONSTANT_PATTERN + ")"
            + "|(?<CONTROL>" + CONTROL_PATTERN + ")"
            + "|(?<TYPE>" + TYPE_PATTERN + ")"
            + "|(?<STORAGE>" + STORAGE_PATTERN + ")"
            + "|(?<OTHERKW>" + OTHER_KEYWORD_PATTERN + ")"
            + "|(?<BUILTIN>" + PYTHON_BUILTIN_PATTERN + ")"
            + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
            + "|(?<FUNCTION>" + FUNCTION_PATTERN + ")"
            + "|(?<CLASS>" + CLASS_PATTERN + ")"
            + "|(?<OPERATOR>" + OPERATOR_PATTERN + ")"
            + "|(?<PAREN>" + PAREN_PATTERN + ")"
            + "|(?<BRACE>" + BRACE_PATTERN + ")"
            + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
        );
    }

    public static StyleSpans<Collection<String>> computeHighlighting(String text, String fileExtension) {
        Pattern pattern = getPatternForExtension(fileExtension);
        Matcher matcher = pattern.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while(matcher.find()) {
            String styleClass = null;
            
            if (matcher.group("COMMENT") != null) styleClass = "comment";
            else if (matcher.group("STRING") != null) styleClass = "string";
            else if (matcher.group("CONSTANT") != null) styleClass = "constant";
            else if (matcher.group("CONTROL") != null) styleClass = "control";
            else if (matcher.group("TYPE") != null) styleClass = "type";
            else if (matcher.group("STORAGE") != null) styleClass = "storage";
            else if (matcher.group("OTHERKW") != null) styleClass = "keyword";
            else if (matcher.group("BUILTIN") != null) styleClass = "builtin";
            else if (matcher.group("FUNCTION") != null) styleClass = "function";
            else if (matcher.group("CLASS") != null) styleClass = "class";
            else if (matcher.group("NUMBER") != null) styleClass = "number";
            else if (matcher.group("OPERATOR") != null) styleClass = "operator";
            else if (matcher.group("PAREN") != null) styleClass = "paren";
            else if (matcher.group("BRACE") != null) styleClass = "brace";
            else if (matcher.group("BRACKET") != null) styleClass = "bracket";
            else {
                try {
                    if (matcher.group("SEMICOLON") != null) styleClass = "semicolon";
                } catch (IllegalArgumentException e) {
                    // SEMICOLON group doesn't exist in Python pattern
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
            return JAVA_PATTERN;
        }
    }
}
