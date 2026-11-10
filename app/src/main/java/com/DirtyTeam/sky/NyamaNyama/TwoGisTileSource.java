package com.DirtyTeam.sky.NyamaNyama;

import org.osmdroid.tileprovider.tilesource.XYTileSource;

public final class TwoGisTileSource extends XYTileSource {

    static final String KEY = "935b0d6a-bd28-4cf0-ad7c-0d2b9cbe56c3";

    public TwoGisTileSource() {
        super(
                "2gis",
                2,
                18,
                256,
                ".png?key=" + KEY,
                new String[]{
                        "https://tile0.maps.2gis.com/v2/tiles/online_hd/",
                        "https://tile1.maps.2gis.com/v2/tiles/online_hd/",
                        "https://tile2.maps.2gis.com/v2/tiles/online_hd/",
                        "https://tile3.maps.2gis.com/v2/tiles/online_hd/",
                        "https://tile4.maps.2gis.com/v2/tiles/online_hd/"
                }
        );
    }
}
