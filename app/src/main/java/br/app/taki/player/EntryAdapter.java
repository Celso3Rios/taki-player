package br.app.taki.player;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public final class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.Holder> {
    public interface Listener {
        void onPlay(MediaEntry item);
        void onFavorite(MediaEntry item, boolean value);
        void onCopyUrl(MediaEntry item);
    }

    private final List<MediaEntry> entries = new ArrayList<>();
    private final Listener listener;

    public EntryAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void replace(List<MediaEntry> data) {
        entries.clear();
        entries.addAll(data);
        notifyDataSetChanged();
    }

    public void append(List<MediaEntry> data) {
        int start = entries.size();
        entries.addAll(data);
        notifyItemRangeInserted(start, data.size());
    }

    @Override
    public long getItemId(int position) { return entries.get(position).id; }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_media, parent, false);
        return new Holder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MediaEntry entry = entries.get(position);
        Context context = holder.itemView.getContext();
        holder.title.setText(entry.name);
        String typeName;
        switch (entry.type) {
            case M3uParser.LIVE: typeName = "Ao vivo"; break;
            case M3uParser.MOVIES: typeName = "Filme"; break;
            case M3uParser.SERIES: typeName = "Série / novela"; break;
            default: typeName = "Outros";
        }
        holder.category.setText(entry.group.isEmpty() ? typeName : typeName + " · " + entry.group);

        if (!entry.logo.isEmpty()) {
            Glide.with(holder.logo)
                    .load(entry.logo)
                    .override(96, 104)
                    .placeholder(R.drawable.ic_taki)
                    .error(R.drawable.ic_taki)
                    .dontAnimate()
                    .into(holder.logo);
        } else {
            Glide.with(holder.logo).clear(holder.logo);
            holder.logo.setImageResource(R.drawable.ic_taki);
        }
        holder.favorite.setImageResource(entry.favorite
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);
        holder.favorite.setColorFilter(context.getColor(
                entry.favorite ? R.color.warning : R.color.muted));
        holder.itemView.setOnClickListener(v -> listener.onPlay(entry));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onCopyUrl(entry);
            return true;
        });
        holder.favorite.setOnClickListener(v -> {
            entry.favorite = !entry.favorite;
            holder.favorite.setImageResource(entry.favorite
                    ? android.R.drawable.btn_star_big_on
                    : android.R.drawable.btn_star_big_off);
            holder.favorite.setColorFilter(context.getColor(
                    entry.favorite ? R.color.warning : R.color.muted));
            listener.onFavorite(entry, entry.favorite);
        });
    }

    @Override
    public int getItemCount() { return entries.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView logo;
        final TextView title;
        final TextView category;
        final ImageButton favorite;

        Holder(View v) {
            super(v);
            logo = v.findViewById(R.id.imageLogo);
            title = v.findViewById(R.id.textTitle);
            category = v.findViewById(R.id.textCategory);
            favorite = v.findViewById(R.id.btnRowFavorite);
        }
    }
}
