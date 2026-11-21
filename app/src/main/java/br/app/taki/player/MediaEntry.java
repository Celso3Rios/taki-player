package br.app.taki.player;

public final class MediaEntry {
    public final long id;
    public final String name;
    public final String url;
    public final String logo;
    public final String group;
    public final int type;
    public boolean favorite;

    public MediaEntry(long id, String name, String url, String logo, String group,
                      int type, boolean favorite) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.logo = logo;
        this.group = group;
        this.type = type;
        this.favorite = favorite;
    }
}
