package com.example.bluetoothjavatls;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class CustomAdapter extends ArrayAdapter<String> {

    public CustomAdapter(Context context, List<String> items) {
        super(context, 0, items);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        String item = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false);
        }

        TextView textView = convertView.findViewById(R.id.itemTextView);
        textView.setText(item);
        // Set the text color to white if not already set in the XML
        textView.setTextColor(getContext().getResources().getColor(android.R.color.white));

        return convertView;
    }
}
