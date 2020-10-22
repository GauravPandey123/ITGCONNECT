package de.kai_morich.simple_usb_terminal;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RecoverySystem;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import de.kai_morich.simple_usb_terminal.adapter.RecieveTextAdapter;
import de.kai_morich.simple_usb_terminal.model.RecieveTextModel;

public class ShowHistoryActivity extends AppCompatActivity {
    private ArrayList<String> movieList = new ArrayList<>();
    private RecyclerView recyclerView;
    private RecieveTextAdapter mAdapter;
    private ImageView imageViewBack;
    private TextView textViewNoDataAviliabe;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.show_history_activity);
        recyclerView = findViewById(R.id.reecyclerViewShowHistory);
        imageViewBack = findViewById(R.id.imageViewBack);
        textViewNoDataAviliabe = findViewById(R.id.textViewNoDataAviliabe);
//        mAdapter = new RecieveTextAdapter(movieList);
//        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
//        recyclerView.setLayoutManager(mLayoutManager);
//        recyclerView.setItemAnimator(new DefaultItemAnimator());
//        recyclerView.setAdapter(mAdapter);
//        setUpData();
        movieList = getArrayList("shrd");

        if (movieList == null) {
            Toast.makeText(ShowHistoryActivity.this, "No Data Found", Toast.LENGTH_LONG).show();
            textViewNoDataAviliabe.setVisibility(View.VISIBLE);
        } else {
            mAdapter = new RecieveTextAdapter(getArrayList("shrd"));
            textViewNoDataAviliabe.setVisibility(View.GONE);
            RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
            recyclerView.setLayoutManager(mLayoutManager);
            recyclerView.setAdapter(mAdapter);
        }

        imageViewBack.setOnClickListener(view -> {
            finish();
        });

    }

    public ArrayList<String> getArrayList(String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Gson gson = new Gson();
        String json = prefs.getString(key, null);
        Type type = new TypeToken<ArrayList<String>>() {
        }.getType();
        return gson.fromJson(json, type);
    }


}
