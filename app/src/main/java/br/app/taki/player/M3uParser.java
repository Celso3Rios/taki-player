package br.app.taki.player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Leitor de listas M3U IPTV sem armazenar os itens em uma lista na memória. */
public final class M3uParser {
    public static final int ALL = 0;
    public static final int LIVE = 1;
    public static final int MOVIES = 2;
    public static final int SERIES = 3;
    public static final int OTHERS = 4;

    private static final Pattern ATTR = Pattern.compile("([\\w-]+)\\s*=\\s*\"([^\"]*)\"");

    private M3uParser() { }

    public interface EntryConsumer {
        void accept(MediaEntry entry);
    }

    public static int parse(Reader source, EntryConsumer consumer) throws IOException {
        BufferedReader input = source instanceof BufferedReader
                ? (BufferedReader) source : new BufferedReader(source, 128 * 1024);
        String line;
        String name = null;
        String logo = "";
        String group = "";
        int total = 0;

        while ((line = input.readLine()) != null) {
            String raw = line.trim();
            if (raw.startsWith("\uFEFF")) raw = raw.substring(1);
            if (raw.isEmpty()) continue;

            if (raw.startsWith("#EXTINF:")) {
                int comma = findOutsideQuotes(raw, ',');
                String header = comma < 0 ? raw : raw.substring(0, comma);
                String displayName = comma < 0 ? "" : raw.substring(comma + 1).trim();
                String tvgName = attribute(header, "tvg-name");
                name = displayName.isEmpty() ? tvgName : displayName;
                logo = attribute(header, "tvg-logo");
                group = attribute(header, "group-title");
            } else if (raw.startsWith("#EXTGRP:") && name != null && group.isEmpty()) {
                group = raw.substring(8).trim();
            } else if (!raw.startsWith("#") && name != null) {
                // Ignorar headers e tags HLS #EXT-X-SESSION-DATA.
                // Em uma playlist M3U, URL vem na linha após o EXTINF.
                String url = raw;
                if (url.startsWith("http://") || url.startsWith("https://") ||
                        url.startsWith("rtsp://") || url.startsWith("rtmp://")) {
                    String title = name.isEmpty() ? "Sem nome" : name;
                    int type = classify(group, url);
                    consumer.accept(new MediaEntry(0, title, url, logo, group, type, false));
                    total++;
                }
                name = null;
                logo = "";
                group = "";
            }
        }
        return total;
    }

    static String attribute(String header, String key) {
        Matcher matcher = ATTR.matcher(header);
        while (matcher.find()) {
            if (matcher.group(1).equalsIgnoreCase(key)) return matcher.group(2).trim();
        }
        return "";
    }

    private static int findOutsideQuotes(String line, char wanted) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') quoted = !quoted;
            if (line.charAt(i) == wanted && !quoted) return i;
        }
        return -1;
    }

    static int classify(String group, String url) {
        String g = group == null ? "" : group.trim().toLowerCase(Locale.ROOT);
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (g.startsWith("canais") || g.startsWith("tv") ||
                u.contains(".m3u8") || u.contains("/live/")) return LIVE;
        if (g.startsWith("series") || g.startsWith("séries") ||
                g.startsWith("novelas")) return SERIES;
        if (g.startsWith("cursos")) return OTHERS;
        if (g.startsWith("filmes") || g.startsWith("colecao") ||
                g.startsWith("coleção") || g.startsWith("shows")) return MOVIES;
        if (u.contains(".mp4") || u.contains(".mkv") || u.contains(".avi")) return MOVIES;
        return OTHERS;
    }
}
