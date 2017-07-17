package org.tymit.displayers.testdisplay.listtestdisplayer;

/**
 * Created by ilan on 1/3/17.
 */
public class TestDisplayConstants {

    public static String JAVASCRIPT =
            "var acc = document.getElementsByClassName(\"accordion\");\n" +
                    "for (var i = 0; i < acc.length; i++) {\n" +
                    "    acc[i].onclick = function(){\n" +
                    "        this.classList.toggle(\"active\");\n" +
                    "        this.nextElementSibling.classList.toggle(\"show\");\n" +
                    "  }\n" +
                    "}";

    public static String CSS =
            "button.accordion {\n" +
                    "    background-color: #eee;\n" +
                    "    color: #444;\n" +
                    "    cursor: pointer;\n" +
                    "    padding: 18px;\n" +
                    "    width: 100%;\n" +
                    "    border: none;\n" +
                    "    text-align: left;\n" +
                    "    outline: none;\n" +
                    "    font-size: 15px;\n" +
                    "    transition: 0.4s;\n" +
                    "}\n" +
                    "button.accordion.active, button.accordion:hover {\n" +
                    "    background-color: #ddd; \n" +
                    "}\n" +
                    "div.panel {\n" +
                    "    padding: 0 18px;\n" +
                    "    display: none;\n" +
                    "    background-color: white;\n" +
                    "}\n" +
                    "div.panel.show {\n" +
                    "    display: block;\n" +
                    "}";

    public static String HEAD = "<head>\n<style>\n" + CSS + "</style>\n</head>\n";

    public static String ROUTE_TITLE_FORMAT =
            "<button class=\"accordion\"> %s (%d hours, %d minutes, %f miles, Degree: %d) </button>\n" +
                    "<div class=\"panel\">\n<ul>";

    public static String ROUTE_ELEMENT_FORMAT = "<li> Walk %d mins, wait %d mins, and travel %d mins to arrive at %s (%f,%f) by %d:%d. </li>\n";

    public static String ROUTE_FOOTER = "</ul>\n</div>\n";


}
