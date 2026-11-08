package com.example.ghostvault.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.ghostvault.R;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** RecyclerView + ViewHolder, following the course's adapter pattern. */
public final class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.Holder> {
    public static final class Entry {
        public final long id, updated;
        public final String title, preview, location;
        public Entry(long id, String title, String preview, String location, long updated) {
            this.id = id;
            this.title = title;
            this.preview = preview;
            this.location = location;
            this.updated = updated;
        }
    }

    public interface Clicks {
        void onTap(long id);
        void onLongPress(long id);
    }
    private final Clicks listener;
    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> visible = new ArrayList<>();
    private String search = "";

    public NotesAdapter(Clicks listener) { this.listener = listener; }

    public void submit(List<Entry> entries) {
        all.clear(); all.addAll(entries); applyFilter(search);
    }

    public int totalCount() { return all.size(); }

    public void applyFilter(String query) {
        search = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        visible.clear();
        for (Entry entry : all) {
            if (search.isEmpty() || entry.title.toLowerCase(Locale.ROOT).contains(search)
                    || entry.preview.toLowerCase(Locale.ROOT).contains(search)
                    || (entry.location != null && entry.location.toLowerCase(Locale.ROOT).contains(search))) {
                visible.add(entry);
            }
        }
        try {
            notifyDataSetChanged();
        } catch (Exception ignored) { }
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new Holder(v);
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Entry entry = visible.get(position);
        holder.title.setText(entry.title.trim().isEmpty() ? "Untitled note" : entry.title);
        holder.preview.setText(entry.preview.trim().isEmpty() ? "No additional text" : entry.preview);
        if (entry.location != null && !entry.location.trim().isEmpty()) {
            holder.locationRow.setVisibility(View.VISIBLE);
            holder.location.setText(entry.location.trim());
        } else {
            holder.locationRow.setVisibility(View.GONE);
        }
        holder.date.setText(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(entry.updated)));
        holder.itemView.setOnClickListener(v -> listener.onTap(entry.id));
        holder.itemView.setOnLongClickListener(v -> { listener.onLongPress(entry.id); return true; });
    }

    @Override public int getItemCount() { return visible.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title, preview, date, location;
        final View locationRow;
        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.noteTitle);
            preview = itemView.findViewById(R.id.notePreview);
            date = itemView.findViewById(R.id.noteUpdated);
            location = itemView.findViewById(R.id.noteLocation);
            locationRow = itemView.findViewById(R.id.noteLocationRow);
        }
    }
}
