package de.kai_morich.simple_usb_terminal.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import de.kai_morich.simple_usb_terminal.R;
import de.kai_morich.simple_usb_terminal.model.RecieveTextModel;

public class RecieveTextAdapter extends RecyclerView.Adapter<RecieveTextAdapter.MyViewHolder> {

    private List<String> moviesList;

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        public TextView title;

        public MyViewHolder(View view) {
            super(view);
            title = (TextView) view.findViewById(R.id.textViewbackgroundcolor);

        }
    }


    public RecieveTextAdapter(List<String> moviesList) {
        this.moviesList = moviesList;
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recivetext_item, parent, false);

        return new MyViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {

        holder.title.setText(moviesList.get(position).toString());

    }

    @Override
    public int getItemCount() {
        return moviesList.size();
    }
}
