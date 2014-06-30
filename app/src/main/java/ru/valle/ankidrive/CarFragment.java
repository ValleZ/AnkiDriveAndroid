package ru.valle.ankidrive;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ToggleButton;

public final class CarFragment extends Fragment {
    private AnkiCarInfo carInfo;

    public CarFragment() {
    }

    public void setCarInfo(AnkiCarInfo carInfo) {
        this.carInfo = carInfo;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_car, container, false);
        ToggleButton lightsButton = (ToggleButton) view.findViewById(R.id.lights_button);
        lightsButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                carInfo.lights(AnkiCarInfo.MASK_LIGHT_TYPE_ENGINE | AnkiCarInfo.MASK_LIGHT_TYPE_FRONTLIGHTS, isChecked);
            }
        });
        final EditText speedEdit = (EditText) view.findViewById(R.id.speed);
        final EditText accelEdit = (EditText) view.findViewById(R.id.accel);
        view.findViewById(R.id.speed_set_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                carInfo.setSpeed(Integer.parseInt(speedEdit.getText().toString()), Integer.parseInt(accelEdit.getText().toString()));
            }
        });
        final EditText laneOffsEdit = (EditText) view.findViewById(R.id.lane_offset);
        view.findViewById(R.id.lane_set_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                carInfo.setOffset(Float.parseFloat(laneOffsEdit.getText().toString()));
            }
        });
        return view;
    }



}
