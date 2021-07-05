package TraceRoute.ea;

import TraceRoute.fitness.Fitness;
import TraceRoute.osm.OpenStreetMap;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Geometry;
import com.github.davidmoten.rtree.geometry.Point;
import com.github.davidmoten.rtree.geometry.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

public class Route {

    private double fitness;

    private List<Point> pointList = new ArrayList<>();

    private Logger logger;

    public Route(Shape shape, double scaleFactor, Point2D.Double center, OpenStreetMap map) {

        logger = LoggerFactory.getLogger(Route.class);

        logger.info("Generating route with s.f. %s centred around %s, %s".formatted(scaleFactor, center.y, center.x));

        // get the center of the provided shape
        // we will translate this origin point to the provided central coords
        Rectangle2D bounds = shape.getBounds2D();
        double centerX = bounds.getCenterX();
        double centerY = bounds.getCenterY();

        // x -> a*x + dx
        // y -> b*y + dy
        AffineTransform transform = new AffineTransform();

        //// IMPORTANT note how the below three transformation lines of code are in seemingly reverse logical order
        //// this is because in matrix transformations we premultiply rather than post multiply.

        // translate to new origin point
        transform.translate(center.x, center.y);

        // scales around the origin point
        transform.scale(scaleFactor, scaleFactor);

        // sets the center as the origin point
        transform.translate(-centerX, -centerY);

        // iterate over the shape
        PathIterator iterator = shape.getPathIterator(transform);

        // the output of iterator.currentSegment is stored into this array
        double[] location = new double[6];
        double x, y;

        // the fitness call

        fitness = Fitness.Perpendicular(map.getTree(), shape.getPathIterator(transform), center.getX(),center.getY(),scaleFactor,0.1); //Does the searching in a 100 meter range

        while (!iterator.isDone()) {
            iterator.currentSegment(location);
            x = location[0];
            y = location[1];

            Entry<String, Geometry> nearest = map.getTree()
                    .nearest(Geometries.pointGeographic(x, y), 100, 1)
                    .toBlocking().singleOrDefault(null);

            // We did not find a vertex nearby. Bad approximation. Stop search.
            if (nearest == null) {
                logger.warn("Did not find a vertex near %s, %s. Stopping calculation for this route.".formatted(y, x));

                fitness = Integer.MIN_VALUE;
                break;
            }

            Rectangle nearestRectangle = nearest.geometry().mbr();
            Point nearestPoint = Geometries.pointGeographic(nearestRectangle.x1(), nearestRectangle.y1());
            pointList.add(nearestPoint);

            iterator.next();
        }

        logger.info("Finished calculating this route. Fitness: %s".formatted(fitness));
    }

    public double getFitness() {
        return fitness;
    }

    public List<Point> getPointList() {
        return pointList;
    }
}
