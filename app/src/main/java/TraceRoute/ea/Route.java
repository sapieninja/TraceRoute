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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;

public class Route {

    private double fitness;
    private double scaleFactor;
    private double X;
    private double Y;
    private double prevX;
    private double prevY;
    private double prevFitness;
    private double prevScaleFactor;
    private Shape shape;
    private OpenStreetMap map;
    private AffineTransform transform;

    private List<Point> pointList = new ArrayList<>();

    private Logger logger;

    public Route(Shape shape, double scaleFactor, Point2D.Double center, double prevX, double prevY, double prevScaleFactor, double prevFitness, OpenStreetMap map) {
        X = center.getX();
        Y = center.getY();
        this.shape = shape;
        this.map = map;
        this.scaleFactor = scaleFactor;
        logger = LoggerFactory.getLogger(Route.class);

        //logger.info("Generating route with s.f. %s centred around %s, %s".formatted(scaleFactor, center.y, center.x));

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
        this.transform = transform;
        // iterate over the shape
        PathIterator iterator = shape.getPathIterator(transform);

        fitness = Fitness.Perpendicular(map.getTree(), shape.getPathIterator(transform), center.getY(),center.getX(),scaleFactor,0.1);
    }
    public List<Double> getChild(double maxDistance, double entropy)
    {
        //To keep things simple we only optimise one value at a time
        int choice = ThreadLocalRandom.current().nextInt(0,3);
        double gradient = 0.0;
        double change = 0.0;
        double oldX, oldY, oldSF;
        oldX = X;
        oldY = Y;
        oldSF = scaleFactor;
        switch (choice)
        {
            case 0: gradient = (prevFitness-fitness)/(X-prevX);
            break;
            case 1: gradient = (prevFitness-fitness)/(Y-prevY);
            break;
            case 2: gradient = (prevFitness-fitness)/(scaleFactor-prevScaleFactor);
            break;
        }
        gradient *= 0.0001;
        change = ThreadLocalRandom.current().nextDouble(-0.01*maxDistance,0.1*maxDistance)*gradient + ThreadLocalRandom.current().nextDouble(0.000000001*entropy);
        change*=0.00000005;
        switch (choice)
        {
            case 0: X = X += change;
                break;
            case 1: Y = Y += change;
                break;
            case 2: scaleFactor = scaleFactor += change;
                break;
        }
        return new ArrayList<Double>(Arrays.asList(scaleFactor, X,Y,oldX,oldY,oldSF,prevFitness));
    }
    public double getFitness() {
        return fitness;
    }

    public List<Point> getPointList() {
        PathIterator iterator = shape.getPathIterator(transform);
        double[] location = new double[6];
        double x, y;
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
        return pointList;
    }
}
