/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package TraceRoute;

import TraceRoute.ea.Conductor;
import TraceRoute.ea.Route;
import TraceRoute.osm.OpenStreetMap;
import TraceRoute.shape.Shape;
import com.github.davidmoten.rtree.geometry.Point;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class App {
    public static void main(String[] args) {
        List<Point2D> route = new LinkedList<>();
        double a,b;
        try {
            OpenStreetMap osm = new OpenStreetMap("../maps/cyclable.osm.pbf");

            File myObj = new File("../pythontests/points");
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                a = Double.parseDouble(data.split(",")[0]);
                b = Double.parseDouble(data.split(",")[1]);
                route.add(new Point2D.Double(a,b));
            }
            System.out.println(route);
            myReader.close();

            //Fitness.Perpendicular(osm.getTree(),route);

            Route optimalRoute = new Conductor(new Shape(route).getPath(), osm).findOptimalRoute();
            //System.out.print("https://www.google.com/maps/dir/");
            for (Point point : optimalRoute.getPointList()) System.out.printf("%s,%s%n", point.y(), point.x());
            //System.out.println();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
