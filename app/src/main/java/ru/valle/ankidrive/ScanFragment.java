package ru.valle.ankidrive;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public final class ScanFragment extends Fragment {
    public static final String TAG = "Anki";
    private GameSession gameSession;
    private ArrayAdapter<AnkiCarInfo> listAdapter;
    private final Handler handler = new Handler();
    private View progressView;
    private OnCarSelectedListener carSelectionListener;

    public ScanFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View parentView = inflater.inflate(R.layout.fragment_scan, container, false);
        progressView = parentView.findViewById(R.id.scan_progress_bar);
        gameSession = GameSession.getInstance(getActivity());
        listAdapter = new ArrayAdapter<AnkiCarInfo>(getActivity(), android.R.layout.simple_list_item_1, gameSession.activeCars);
        ListView listView = (ListView) parentView.findViewById(R.id.list);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                carSelectionListener.onCarSelected(gameSession.activeCars.get(position));
            }
        });
        parentView.findViewById(R.id.scan_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scan();
            }
        });
        scan();
        return parentView;
    }

    private void scan() {
        if (!gameSession.scanningForDevices) {
            handler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    Log.d(TAG, "scan finished " + gameSession.activeCars.size());
                    gameSession.stopScanning();
                    progressView.setVisibility(View.GONE);
                    for (AnkiCarInfo carInfo : gameSession.activeCars) {
                        carInfo.connect(getActivity().getApplicationContext());
                    }
                }
            }, 2000);
            gameSession.closeAllConnections();
            listAdapter.notifyDataSetChanged();
            progressView.setVisibility(View.VISIBLE);
            gameSession.startScanning();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        carSelectionListener = (OnCarSelectedListener) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        carSelectionListener = null;
    }

}
