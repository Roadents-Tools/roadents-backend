package com.reroute.displayers.testdisplay.maproutedrawer;

public class RouteMapsPageConstants {

    public static final String HTML_FORMAT = "<!DOCTYPE html>\n" +
            "<html>\n" +
            "  <head>\n" +
            "    <title>Simple Map</title>\n" +
            "    <meta name=\"viewport\" content=\"initial-scale=1.0\">\n" +
            "    <meta charset=\"utf-8\">\n" +
            "    <style>\n" +
            "      /* Always set the map height explicitly to define the size of the div\n" +
            "       * element that contains the map. */\n" +
            "      #map {\n" +
            "        height: 100%%;\n" +
            "      }\n" +
            "      /* Optional: Makes the sample page fill the window. */\n" +
            "      html, body {\n" +
            "        height: 100%%;\n" +
            "        margin: 0;\n" +
            "        padding: 0;\n" +
            "      }\n" +
            "    </style>\n" +
            "  </head>\n" +
            "  <body>\n" +
            "    <div id=\"map\"></div>\n" +
            "    <script>\n" +
            "        %s" +
            "    </script>\n" +
            "    <script src=\"https://maps.googleapis.com/maps/api/js?key=%s&callback=initMap\"\n" +
            "    async defer></script>\n" +
            "  </body>\n" +
            "</html>";

    public static final String JS_FORMAT =
            "var citymap = { %s };\n" +
                    "\n" +
                    "function initMap() {\n" +
                    "  // Create the map.\n" +
                    "  var map = new google.maps.Map(document.getElementById('map'), {\n" +
                    "    zoom: 4,\n" +
                    "    center: {lat: 37.090, lng: -95.712},\n" +
                    "    mapTypeId: 'terrain'\n" +
                    "  });\n" +
                    "\n" +
                    "  // Construct the circle for each value in citymap.\n" +
                    "  for (var name in citymap) {\n" +
                    "    // Add the circle for this city to the map.\n" +
                    "    var cityLine = new google.maps.Polyline({\n" +
                    "      strokeColor: citymap[name].color,\n" +
                    "      path : citymap[name].path,\n" +
                    "      strokeOpacity: 0.8,\n" +
                    "      strokeWeight: 2,\n" +
                    "      map: map,\n" +
                    "    });\n" +
                    "  }\n" +
                    "}";

    public static final String PATH_FORMAT = "\"%s\": {path : [%s], color : \"#%s\"}";
    public static final String PATH_ITEM_FORMAT = "    {lat : %f, lng : %f}\n";
}
