package br.app.taki.player;

import org.junit.Test;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class M3uParserTest {
    @Test
    public void ignoraExtXSessionDataELeTodosOsItens() throws Exception {
        String playlist = "\uFEFF#EXTM3U\n" +
                "#EXT-X-SESSION-DATA:DATA-ID=\"com.xui.1\"\n" +
                "#EXTINF:-1 tvg-name=\"Filme, 2025\" tvg-logo=\"https://logo.exemplo/a.png\" " +
                "group-title=\"Filmes | Ação\",Filme, 2025\n" +
                "http://exemplo.invalid/video.mp4\n" +
                "#EXTINF:-1 group-title=\"Canais | Brasil\",Canal 1\n" +
                "http://exemplo.invalid/live/teste.m3u8\n";
        List<MediaEntry> items = new ArrayList<>();
        int count = M3uParser.parse(new StringReader(playlist), items::add);
        assertEquals(2, count);
        assertEquals("Filme, 2025", items.get(0).name);
        assertEquals("Filmes | Ação", items.get(0).group);
        assertEquals("https://logo.exemplo/a.png", items.get(0).logo);
        assertEquals(M3uParser.MOVIES, items.get(0).type);
        assertEquals(M3uParser.LIVE, items.get(1).type);
    }

    @Test
    public void identificaSeriesNovelasECursos() throws Exception {
        String playlist = "#EXTM3U\n" +
                "#EXTINF:-1 group-title=\"Series | Netflix\",Ep1\nhttps://exemplo.invalid/e1.mp4\n" +
                "#EXTINF:-1 group-title=\"Novelas\",Cap1\nhttps://exemplo.invalid/e2.mp4\n" +
                "#EXTINF:-1 group-title=\"Cursos | Informática\",Aula\nhttps://exemplo.invalid/e3.mp4\n";
        List<MediaEntry> items = new ArrayList<>();
        assertEquals(3, M3uParser.parse(new StringReader(playlist), items::add));
        assertEquals(M3uParser.SERIES, items.get(0).type);
        assertEquals(M3uParser.SERIES, items.get(1).type);
        assertEquals(M3uParser.OTHERS, items.get(2).type);
    }

    @Test
    public void descartaLinhasQueNaoSaoUrls() throws Exception {
        String playlist = "#EXTM3U\n#EXTINF:-1,Item1\n#NOT-URL\n" +
                "#EXTINF:-1,Item2\n\ntexto invalido\n";
        List<MediaEntry> items = new ArrayList<>();
        assertEquals(0, M3uParser.parse(new StringReader(playlist), items::add));
    }
}
