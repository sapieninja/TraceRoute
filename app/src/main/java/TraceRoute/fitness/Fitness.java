package TraceRoute.fitness;

import TraceRoute.ea.Route;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Geometry;
import com.github.davidmoten.rtree.geometry.internal.LineDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.util.LinkedList;
import java.util.List;

public class Fitness {
    private static Logger logger;
    /**
     * Given the line segments p1 to p2 and p3 to p4
     * this function tells where they intersect (may be outside of the segment)
     * https://en.wikipedia.org/wiki/Line%E2%80%93line_intersection
     *
     * @param p1 The first point of the first line
     * @param p2 The second point of the first line
     * @param p3 The first point of the second line
     * @param p4 The second point of the second line
     * @return The point of intersection of the two lines
     */
    public static Point2D intersection(Point2D p1, Point2D p2, Point2D p3, Point2D p4) {
        double denominator, px, py;
        denominator = (p1.getX() - p2.getX()) * (p3.getY() - p4.getY()) - (p1.getY() - p2.getY()) * (p3.getX() - p4.getX());
        if (denominator == 0) {
            throw new IllegalArgumentException();
        }
        px = ((p1.getX() * p2.getY() - p1.getY() * p2.getX()) * (p3.getX() - p4.getX()) - (p1.getX() - p2.getX()) * (p3.getX() * p4.getY() - p3.getY() * p4.getX())) / denominator;
        py = ((p1.getX() * p2.getY() - p1.getY() * p2.getX()) * (p3.getY() - p4.getY()) - (p1.getY() - p2.getY()) * (p3.getX() * p4.getY() - p3.getY() * p4.getX())) / denominator;
        return new Point2D.Double(px, py);
    }

    /**
     * Checks to see if a is between b and c
     *
     * @param a The number to check
     * @param b One bound of the range
     * @param c Another bound of the range
     * @return
     */
    public static boolean between(double a, double b, double c) {
        if (b > c) {
            return a >= c && b >= a;
        } else if (c > b) {
            return a >= b && c >= a;
        }
        return false;
    }

    /**
     * Checks to see that two line segments intersect
     *
     * @param p1
     * @param p2
     * @param p3
     * @param p4
     * @return Boolean to see if the two lines intersect
     */
    public static Boolean intersect(Point2D p1, Point2D p2, Point2D p3, Point2D p4) {
        try {
            Point2D intersection = intersection(p1, p2, p3, p4);
            return between(intersection.getX(), p1.getX(), p2.getX()) && between(intersection.getX(), p3.getX(), p4.getX()) && between(intersection.getY(), p1.getY(), p2.getY()) & between(intersection.getY(), p3.getY(), p4.getY());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Uses pythagoras to find the distance between two points
     *
     * @param p1 The first point
     * @param p2 The second point
     * @return
     */
    public static double distance(Point2D p1, Point2D p2) {
        return Math.sqrt(Math.pow(p1.getX() - p2.getX(), 2) + Math.pow(p1.getY() - p2.getY(), 2));
    }

    public static Double Perpendicular(RTree<String, Geometry> london, PathIterator pathiterator, double dx, double dy, double scale, double searchdist) {
        double score = 0;
        double x, y, prevx, prevy, gradient, stepdist, step, totaldist;
        double lon1, lat1, lon2, lat2;
        double minimum = 0.0;
        double[] location = new double[2];
        logger = LoggerFactory.getLogger(Route.class);
        LinkedList<Point2D> pointslist = new LinkedList<>();
        while (!pathiterator.isDone()) {
            pathiterator.currentSegment(location);
            Point2D point = new Point2D.Double(location[0], location[1]);
            pointslist.add(point);
            pathiterator.next();
        }
        LinkedList<Double> results = new LinkedList<>();
        totaldist = 0;
        Point2D intersection;
        List<LineDouble> lines = new LinkedList<>();
        Point2D last = pointslist.get(pointslist.size() - 1);
        prevx = last.getX() * scale + dx;
        prevy = last.getY() * scale + dy;
        for (Point2D coordinate : pointslist
        ) {
            y = coordinate.getX() * scale + dx;
            x = coordinate.getY() * scale + dy;
            gradient = -1 / ((y - prevy) / (x - prevx));
            if (gradient == Double.NEGATIVE_INFINITY) {
                gradient = -100;
            }
            if (gradient == Double.POSITIVE_INFINITY) {
                gradient = 100;
            }
            stepdist = Math.sqrt(1 + Math.pow(gradient, 2));
            step = (searchdist / 111) / stepdist;
            lon1 = x + step;
            lat1 = y + step * gradient;
            lon2 = x - step;
            lat2 = y - step * gradient;
            Iterable<Entry<String, Geometry>> online = london.search(Geometries.pointGeographic(x,y),searchdist).toBlocking().toIterable();
            lines = new LinkedList<>();
            minimum = results.stream().mapToDouble(a -> a).average().orElse(0) * 2;
            if (minimum == 0.0) minimum = 0.1;
            for (Entry part : online
            ) {
                if (part.geometry().getClass().getSimpleName().equals("LineDouble")) {
                    LineDouble line = (LineDouble) part.geometry();
                    if (intersect(new Point2D.Double(lon1, lat1), new Point2D.Double(lon2, lat2), new Point2D.Double(line.x1(), line.y1()), new Point2D.Double(line.x2(), line.y2()))) {
                        intersection = intersection(new Point2D.Double(lon1, lat1), new Point2D.Double(lon2, lat2), new Point2D.Double(line.x1(), line.y1()), new Point2D.Double(line.x2(), line.y2()));
                        if (distance(intersection, new Point2D.Double(x, y)) / 111 < minimum) {
                            minimum = distance(intersection, new Point2D.Double(x, y)) / 111;
                        }
                    }
                }
            }
            logger.info("Completed section" + pointslist.indexOf(coordinate));
            results.add(minimum);
            totaldist += minimum;
            minimum = 0.0;
            prevx = x;
            prevy = y;
         }
        return totaldist / scale;
    }
}
