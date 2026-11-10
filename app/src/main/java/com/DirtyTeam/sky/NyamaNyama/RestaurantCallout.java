package com.DirtyTeam.sky.NyamaNyama;

import android.view.View;
import android.widget.TextView;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

final class RestaurantCallout extends InfoWindow {

    RestaurantCallout(MapView mapView) {
        super(R.layout.map_restaurant_popup, mapView);
        mView.setOnClickListener(v -> close());
    }

    @Override
    public void onOpen(Object item) {
        Marker marker = (Marker) item;
        TextView title = mView.findViewById(R.id.map_popup_title);
        TextView address = mView.findViewById(R.id.map_popup_address);
        title.setTypeface(AppFonts.montserratBold(mView.getContext()));
        address.setTypeface(AppFonts.montserratRegular(mView.getContext()));
        title.setText(marker.getTitle());
        String snippet = marker.getSnippet();
        if (snippet == null || snippet.isEmpty()) {
            address.setVisibility(View.GONE);
        } else {
            address.setVisibility(View.VISIBLE);
            address.setText(snippet);
        }
    }

    @Override
    public void onClose() {
    }
}
