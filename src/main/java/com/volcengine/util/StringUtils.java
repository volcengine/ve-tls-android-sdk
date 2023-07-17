package com.volcengine.util;

import java.util.Iterator;
import java.util.Objects;

public class StringUtils {
    public static final String EMPTY = "";
    private static final int STRING_BUILDER_SIZE = 256;

    private StringUtils() {
    }

    public static String replaceWhiteSpaceCharacter(String str) {
        if (str == null) {
            return null;
        }
        return str.replace("\n", "\\n").replace("\r", "\\r").
                replace("\t", "\\t");
    }

    public static boolean isNotEmpty(final CharSequence cs) {
        return !isEmpty(cs);
    }

    public static boolean isEmpty(final CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static boolean isBlank(final CharSequence cs) {
        final int strLen = length(cs);
        if (strLen == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static int length(final CharSequence cs) {
        return cs == null ? 0 : cs.length();
    }

    public static boolean isNotBlank(final CharSequence cs) {
        return !isBlank(cs);
    }

    public static String join(final Iterable<?> iterable, final char separator) {
        if (iterable == null) {
            return null;
        }
        return join((Iterable<?>) iterable.iterator(), separator);
    }

    public static String join(final Iterable<?> iterable, final String separator) {
        if (iterable == null) {
            return null;
        }
        return join(iterable.iterator(), separator);
    }

    public static String join(final Iterator<?> iterator, final String separator) {

        // handle null, zero and one elements before building a buffer
        if (iterator == null) {
            return null;
        }
        if (!iterator.hasNext()) {
            return EMPTY;
        }
        final Object first = iterator.next();
        if (!iterator.hasNext()) {
            return Objects.toString(first, "");
        }

        // two or more elements
        final StringBuilder buf = new StringBuilder(STRING_BUILDER_SIZE); // Java default is 16, probably too small
        if (first != null) {
            buf.append(first);
        }

        while (iterator.hasNext()) {
            if (separator != null) {
                buf.append(separator);
            }
            final Object obj = iterator.next();
            if (obj != null) {
                buf.append(obj);
            }
        }
        return buf.toString();
    }

    public static String join(final Object[] array, final String separator) {
        if (array == null) {
            return null;
        }
        return join(array, separator, 0, array.length);
    }

    public static String join(final Object[] array, String separator, final int startIndex, final int endIndex) {
        if (array == null) {
            return null;
        }
        if (separator == null) {
            separator = EMPTY;
        }

        // endIndex - startIndex > 0:   Len = NofStrings *(len(firstString) + len(separator))
        //           (Assuming that all Strings are roughly equally long)
        final int noOfItems = endIndex - startIndex;
        if (noOfItems <= 0) {
            return EMPTY;
        }

        final StringBuilder buf = newStringBuilder(noOfItems);

        if (array[startIndex] != null) {
            buf.append(array[startIndex]);
        }

        for (int i = startIndex + 1; i < endIndex; i++) {
            buf.append(separator);

            if (array[i] != null) {
                buf.append(array[i]);
            }
        }
        return buf.toString();
    }

    private static StringBuilder newStringBuilder(final int noOfItems) {
        return new StringBuilder(noOfItems * 16);
    }

}
