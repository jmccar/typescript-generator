
package cz.habarta.typescript.generator;

import cz.habarta.typescript.generator.parser.*;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Input {

    private final List<SourceType<Type>> sourceTypes;

    private Input(List<SourceType<Type>> sourceTypes) {
        this.sourceTypes = sourceTypes;
    }

    public List<SourceType<Type>> getSourceTypes() {
        return sourceTypes;
    }

    public static Input from(Type... types) {
        final List<SourceType<Type>> sourceTypes = new ArrayList<>();
        for (Type type : types) {
            sourceTypes.add(new SourceType<>(type));
        }
        return new Input(sourceTypes);
    }

    private static Input fromClassNames(List<String> classNames, ClassLoader classLoader) {
        try {
            final List<SourceType<Type>> types = new ArrayList<>();
            for (String className : classNames) {
                types.add(new SourceType<Type>(classLoader.loadClass(className), null, null));
            }
            return new Input(types);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Input fromClassNamePatterns(List<String> classNamePatterns, ClassLoader classLoader) {
        final FastClasspathScanner scanner = new FastClasspathScanner().scan();
        final List<String> allClassNames = new ArrayList<>();
        allClassNames.addAll(scanner.getNamesOfAllStandardClasses());
        allClassNames.addAll(scanner.getNamesOfAllInterfaceClasses());
        final List<String> classNames = filterClassNames(allClassNames, classNamePatterns);
        return fromClassNames(classNames, classLoader);
    }

    private static Input fromJaxrsApplication(String jaxrsApplicationClassName, List<String> excludedClassNames, ClassLoader classLoader) {
        final List<SourceType<Type>> sourceTypes = new JaxrsApplicationScanner(classLoader).scanJaxrsApplication(jaxrsApplicationClassName, excludedClassNames);
        return new Input(sourceTypes);
    }

    public static Input fromClassNamesAndJaxrsApplication(List<String> classNames, List<String> classNamePatterns, String jaxrsApplicationClassName, List<String> excludedClassNames, ClassLoader classLoader) {
        final List<SourceType<Type>> types = new ArrayList<>();
        if (classNames != null) {
            types.addAll(fromClassNames(classNames, classLoader).getSourceTypes());
        }
        if (classNamePatterns != null) {
            types.addAll(fromClassNamePatterns(classNamePatterns, classLoader).getSourceTypes());
        }
        if (jaxrsApplicationClassName != null) {
            types.addAll(fromJaxrsApplication(jaxrsApplicationClassName, excludedClassNames, classLoader).getSourceTypes());
        }
        if (types.isEmpty()) {
            final String errorMessage = "No input classes found.";
            System.out.println(errorMessage);
            throw new RuntimeException(errorMessage);
        }
        return new Input(types);
    }

    static List<String> filterClassNames(List<String> classNames, List<String> globs) {
        final List<Pattern> regexps = new ArrayList<>();
        for (String glob : globs) {
            regexps.add(globToRegexp(glob));
        }
        final List<String> result = new ArrayList<>();
        for (String className : classNames) {
            for (Pattern regexp : regexps) {
                if (regexp.matcher(className).matches()) {
                    result.add(className);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Creates regexp for glob pattern.
     * Replaces "*" with "[^.\$]*" and "**" with ".*".
     */
    static Pattern globToRegexp(String glob) {
        final Pattern globToRegexpPattern = Pattern.compile("(\\*\\*)|(\\*)");
        final Matcher matcher = globToRegexpPattern.matcher(glob);
        final StringBuffer sb = new StringBuffer();
        int lastEnd = 0;
        while (matcher.find()) {
            sb.append(Pattern.quote(glob.substring(lastEnd, matcher.start())));
            if (matcher.group(1) != null) {
                sb.append(Matcher.quoteReplacement(".*"));
            }
            if (matcher.group(2) != null) {
                sb.append(Matcher.quoteReplacement("[^.$]*"));
            }
            lastEnd = matcher.end();
        }
        sb.append(Pattern.quote(glob.substring(lastEnd, glob.length())));
        return Pattern.compile(sb.toString());
    }

}
