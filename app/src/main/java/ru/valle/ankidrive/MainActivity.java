package ru.valle.ankidrive;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;


public final class MainActivity extends Activity implements OnCarSelectedListener {
    private static final String TAG = "Anki";

    private GameSession gameSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        gameSession = GameSession.getInstance(this);
    }


    @Override
    protected void onStop() {
        super.onStop();
        gameSession.stopScanning();
        gameSession.closeAllConnections();
    }

    @Override
    public void onCarSelected(AnkiCarInfo carInfo) {
        Log.d(TAG, "car selected " + carInfo);
        CarFragment carFragment = new CarFragment();
        carFragment.setCarInfo(carInfo);
        getFragmentManager().beginTransaction().add(R.id.fragment_container, carFragment).addToBackStack(null).commit();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

    }
}
