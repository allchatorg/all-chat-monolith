package com.mk3.chatapp.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strips the chat formatting markers (**bold**, *italic*, ***bold italic***)
 * from message content, keeping literal asterisks and URLs intact.
 *
 * Must stay behaviorally identical to tokenize() in the frontend's
 * src/features/chatroom/utils/messageMarkers.ts — that file is the
 * wire-format reference. Rules mirrored from it:
 * - markers never pair across newlines,
 * - asterisks inside URLs are URL text, trailing asterisks after a URL are
 *   formatting,
 * - a run can open only when followed by non-whitespace and close only when
 *   preceded by non-whitespace; unmatched runs stay literal,
 * - runs of 3+ asterisks act as *** with the excess kept literal.
 */
public final class MessageMarkers {

    // Same as URL_REGEX in messageMarkers.ts; UNICODE_CHARACTER_CLASS aligns
    // Java's \s with the JS whitespace class.
    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://\\S+", Pattern.UNICODE_CHARACTER_CLASS);

    private MessageMarkers() {
    }

    private record UrlRange(int start, int end) {
    }

    private record AsteriskRun(int start, int length, boolean canOpen, boolean canClose) {
    }

    public static String strip(String text) {
        if (text == null) {
            return null;
        }
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            stripLine(lines[i], out);
        }
        return out.toString();
    }

    private static List<UrlRange> findUrlRanges(String line) {
        List<UrlRange> ranges = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(line);
        while (matcher.find()) {
            int end = matcher.end();
            while (end > matcher.start() && line.charAt(end - 1) == '*') {
                end--;
            }
            if (end > matcher.start()) {
                ranges.add(new UrlRange(matcher.start(), end));
            }
        }
        return ranges;
    }

    private static boolean isBoldPart(int length) {
        return length >= 2;
    }

    private static boolean isItalicPart(int length) {
        return length == 1 || length >= 3;
    }

    private static boolean isWhitespace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    private static boolean closerExists(List<AsteriskRun> runs, int fromIdx, boolean boldPart) {
        for (int i = fromIdx; i < runs.size(); i++) {
            AsteriskRun run = runs.get(i);
            boolean part = boldPart ? isBoldPart(run.length()) : isItalicPart(run.length());
            if (part && run.canClose()) {
                return true;
            }
        }
        return false;
    }

    private static void stripLine(String line, StringBuilder out) {
        List<UrlRange> urls = findUrlRanges(line);
        boolean[] inUrl = new boolean[line.length()];
        for (UrlRange url : urls) {
            for (int i = url.start(); i < url.end(); i++) {
                inUrl[i] = true;
            }
        }

        List<AsteriskRun> runs = new ArrayList<>();
        for (int i = 0; i < line.length(); ) {
            if (line.charAt(i) == '*' && !inUrl[i]) {
                int j = i;
                while (j < line.length() && line.charAt(j) == '*' && !inUrl[j]) {
                    j++;
                }
                boolean canOpen = j < line.length() && !isWhitespace(line.charAt(j));
                boolean canClose = i > 0 && !isWhitespace(line.charAt(i - 1));
                runs.add(new AsteriskRun(i, j - i, canOpen, canClose));
                i = j;
            } else {
                i++;
            }
        }

        boolean bold = false;
        boolean italic = false;
        int urlIdx = 0;
        int runIdx = 0;
        for (int i = 0; i < line.length(); ) {
            if (urlIdx < urls.size() && urls.get(urlIdx).start() == i) {
                UrlRange url = urls.get(urlIdx++);
                out.append(line, url.start(), url.end());
                i = url.end();
                continue;
            }
            AsteriskRun run = runIdx < runs.size() && runs.get(runIdx).start() == i
                    ? runs.get(runIdx) : null;
            if (run == null) {
                out.append(line.charAt(i));
                i++;
                continue;
            }
            runIdx++;
            int literal = run.length() >= 3 ? run.length() - 3 : 0;
            if (isBoldPart(run.length())) {
                if (bold && run.canClose()) {
                    bold = false;
                } else if (!bold && run.canOpen() && closerExists(runs, runIdx, true)) {
                    bold = true;
                } else {
                    literal += run.length() >= 3 ? 2 : run.length();
                }
            }
            if (isItalicPart(run.length())) {
                if (italic && run.canClose()) {
                    italic = false;
                } else if (!italic && run.canOpen() && closerExists(runs, runIdx, false)) {
                    italic = true;
                } else {
                    literal += run.length() >= 3 ? 1 : run.length();
                }
            }
            out.append("*".repeat(literal));
            i = run.start() + run.length();
        }
    }
}
