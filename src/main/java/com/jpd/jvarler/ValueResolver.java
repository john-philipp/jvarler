package com.jpd.jvarler;

import com.jpd.utils.Resolver;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static com.jpd.utils.JVarlerUtils.getInnerKeyMatches;
import static com.jpd.utils.JVarlerUtils.getRelPathMatches;
import static com.jpd.utils.JVarlerUtils.j;

/** Value resolver for varler syntax. */
public class ValueResolver extends Resolver {

    /** The generic prefix is the variable start.
     * The underlying variable is relative to the local context.
     */
    private static final String GENERIC_PREFIX = "${";

    /** The root prefix indicates a variable start.
     * The underlying variable is relative to the root context.
     * (Starting with `/`.)
     */
    private static final String ROOT_PREFIX = "${/";

    /** The suffix indicates the end of a variable. */
    private static final String SUFFIX = "}";

    /** The joiner indicates a nesting into a collection. */
    private static final String JOINER = "/";

    /** Same as joiner, just opposite. */
    private static final String SEPARATOR = "[" + JOINER + "]";

    /** The relative path prefix indicates variable resolution
     * relative to the context one level up. Can be repeated.
     */
    private static final String RELATIVE_PATH_PREFIX = "../";

    /** Helper class to keep track of context during
     * variable pre-processing.
     */
    private static class Context {

        /** Helper class: generic container of values (map/list). */
        private static class Container {

            /** Underlying collection. */
            private final Object collection;

            /** ID. */
            private final Object id;

            /** Constructor. */
            private Container(Object collection, Object id) {
                this.collection = collection;
                this.id = id;
            }

            /** Update collection with value. */
            private void update(Object value) {
                if (collection instanceof Map) {
                    assert id instanceof String;
                    ((Map<String, Object>) collection).put((String) id, value);
                } else if (collection instanceof List) {
                    assert id instanceof Integer;
                    ((List<Object>) collection).set((Integer) id, value);
                } else {
                    throw new RuntimeException("Unexpected, container neither list nor map.");
                }
            }
        }

        /** Current path from root. */
        private String currPath = "";

        /** Current collection container. */
        private Container container;

        /** Current container stack. */
        private Deque<Container> containerStack = new ArrayDeque<>();

        /** Set container. */
        private void setContainer(Container container) {
            containerStack.add(container);
            this.container = container;
        }

        /** Pop container stack (de-nest). */
        private void popContainerStack() {
            containerStack.pollLast();
            container = containerStack.peekLast();
        }

        /** Change into relative path. */
        private String cd(String relPath, boolean dry) {
            String[] currPathParts = currPath.split(SEPARATOR);
            String[] relPathParts = relPath.split(SEPARATOR);

            int len1 = currPathParts.length;
            int len2 = relPathParts.length;
            int offset = 0;
            for (String relPathPart : relPathParts) {
                if (relPathPart.equals("..")) {
                    offset++;
                }
            }
            String path1 = j(JOINER, Arrays.copyOfRange(currPathParts, 0, len1 - offset));
            String path2 = j(JOINER, Arrays.copyOfRange(relPathParts, offset, len2));
            String newPath = path1 + JOINER + path2;
            if (!dry) {
                currPath = newPath;
            }
            return newPath;
        }

        /** Go up a directory. */
        private String up(int i, boolean dry) {
            String path = currPath;
            while (i-- > 0) {
                path = cd("..", dry);
                if (!dry) {
                    popContainerStack();
                }
            }
            return path;
        }
    }

    /** Constructor. */
    public ValueResolver() {
        super();
        setNestedResolution(true);
        setWrapper(ROOT_PREFIX, SUFFIX);
        setSeparator(SEPARATOR);
        setJoiner(JOINER);
    }

    /** Pre-process unresolved values before attempting resolution. */
    @Override
    protected void preProcessUnresolved(Object unresolved) {
        preProcessUnresolved(unresolved, new Context());
    }

    /** Pre-process unresolved values before attempting resolution.
     * Specifically, we escape any local and relative paths to
     * root path equivalents.
     *
     * The underlying resolver can then be left as is.
     */
    protected void preProcessUnresolved(Object unresolved, Context context) {
        if (unresolved instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) unresolved;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                nestIntoUnresolved(context, map, key, key, entry.getValue());
            }
        } else if (unresolved instanceof List) {
            List<Object> list = (List<Object>) unresolved;
            for (int i = 0; i < list.size(); i++) {
                nestIntoUnresolved(context, list, "[" + i + "]", i, list.get(i));
            }
        } else if (unresolved instanceof String) {
            String string = (String) unresolved;
            List<String> groups = getRelPathMatches(string);
            if (groups.isEmpty()) {

                // Local paths must be prefixed with paths one level up.
                String upDir = context.up(1, true);
                groups = getInnerKeyMatches(string);
                if (!groups.isEmpty()) {
                    for (String group : groups) {
                        if (group.startsWith("/")) {
                            // Path already from root.
                            continue;
                        }
                        StringBuilder pathFromRoot = new StringBuilder();
                        pathFromRoot.append(upDir);
                        if (!upDir.endsWith(JOINER)) {
                            pathFromRoot.append(JOINER);
                        }
                        pathFromRoot.append(group);
                        string = string.replace(wrapVar(group), wrapVar(pathFromRoot.toString()));
                    }
                }
                context.container.update(string);
                return;
            }
            for (String group : groups) {
                String pathFromRoot = context.cd(RELATIVE_PATH_PREFIX + group, true);
                string = string.replace(GENERIC_PREFIX + group, GENERIC_PREFIX + pathFromRoot);
                context.container.update(string);
            }
        }
    }

    /** Helper: wrap var. */
    private String wrapVar(String key) {
        return GENERIC_PREFIX + key + SUFFIX;
    }

    /** Nest into unresolved collection. Keep track of context. */
    private void nestIntoUnresolved(Context context, Object collection, String relDir, Object key, Object value) {
        context.cd(relDir, false);
        context.setContainer(new Context.Container(collection, key));
        preProcessUnresolved(value, context);
        context.up(1, false);
    }
}
