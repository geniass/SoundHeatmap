package geniass.soundmap;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;

import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: ari
 * Date: 5/4/13
 * Time: 11:53 PM
 * To change this template use File | Settings | File Templates.
 */
public class HeatmapOverlay {
    BitmapDescriptor image;
    GoogleMap map;
    HashMap<Coordinate, Decibels> points;

    public HeatmapOverlay(GoogleMap map, HashMap<Coordinate, Decibels> points) {
        this.image = image;
        this.map = map;
        this.points = points;

    }
}