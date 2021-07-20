package TraceRoute.ea;

import TraceRoute.fitness.Fitness;
import TraceRoute.osm.OpenStreetMap;
import com.github.davidmoten.rtree.*;
import com.github.davidmoten.rtree.geometry.Point;
import com.github.davidmoten.rtree.geometry.Rectangle;
import com.github.davidmoten.rtree.geometry.*;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Route {

    private static Logger logger;
    private final Serializer<String, Geometry> serializer = Serializers.flatBuffers().utf8();
    private double fitness;
    private double scaleFactor;
    private double X;
    private double Y;
    private final double prevX;
    private final double prevY;
    private final double prevFitness;
    private final double prevScaleFactor;
    private final Shape shape;
    private final OpenStreetMap map;
    private final AffineTransform transform;
    private final List<Point> pointList = new ArrayList<>();
    private RTree<String, Geometry> intersectTree;
    private final String filename = "intersectTree";

    public Route(Shape shape, double scaleFactor, Point2D.Double center, double prevX, double prevY, double prevScaleFactor, double prevFitness, OpenStreetMap map) {
        X = center.getX();
        Y = center.getY();
        this.shape = shape;
        this.map = map;
        this.scaleFactor = scaleFactor;
        this.prevFitness = prevFitness;
        this.prevX = prevX;
        this.prevY = prevY;
        this.prevScaleFactor = prevScaleFactor;
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

        fitness = Fitness.Perpendicular(map.getTree(), shape.getPathIterator(transform), center.getY(), center.getX(), scaleFactor, 0.1);
        //TODO : Fix the fact that the previous thing will be the same if it was not randomly changed last time.
        //TODO : Fix the fact that the best thing destroys all other contenders (so it converges and then doesn't improve)
        //TODO : Fix the fact that the gradient descent seems to be slightly broken
        //TODO : If I can't get it so that it doesn't immediately converge on the first half decent solution, instead widen the generations and run them for less time => more random less optimisation
        //TODO : get rid of newborn bonus it obviously doesn't work
    }

    public List<Double> getChild(double maxDistance, double entropy) {
        //To keep things simple we only optimise one value at a time
        int choice = ThreadLocalRandom.current().nextInt(0, 3);
        double gradient = 0.0;
        double change = 0.0;
        double oldX, oldY, oldSF;
        oldX = prevX;
        oldY = prevY;
        oldSF = prevScaleFactor;
        switch (choice) {
            case 0:
                gradient = -(this.prevFitness - this.fitness) / (this.X - this.prevX + 0.00000000000000001);
                oldX = X;
                break;
            case 1:
                gradient = -(this.prevFitness - this.fitness) / (this.Y - this.prevY + 0.00000000000000001);
                oldY = Y;
                break;
            case 2:
                gradient = -(this.prevFitness - this.fitness) / (this.scaleFactor - this.prevScaleFactor + 0.0000000000000000001);
                oldSF = fitness;
                break;
        }
        if (gradient == Double.POSITIVE_INFINITY) {
            gradient = 100000;
        } else if (gradient == Double.NEGATIVE_INFINITY) {
            gradient = -100000;
        }
        gradient *= 0.001;
        change = ThreadLocalRandom.current().nextDouble(-0.01 * maxDistance, 0.1 * maxDistance) * gradient + ThreadLocalRandom.current().nextDouble(0.0000000001 * entropy);
        change *= 0.0000005;
        switch (choice) {
            case 0:
                this.X = X += change;
                break;
            case 1:
                this.Y = Y += change;
                break;
            case 2:
                scaleFactor = scaleFactor += change;
                break;
        }
        return new ArrayList<Double>(Arrays.asList(scaleFactor, X, Y, oldX, oldY, oldSF, fitness));
    }

    public double getFitness() {
        return fitness;
    }

    //TODO make this bit find the nearest INTERSECTION
    private void makeTree(String filename) throws IOException {
        double startTime;
        HashSet<Line> found = new HashSet<>();
        int count = 1;
        Point toInsert;
        List<Line> segments = new LinkedList<>();
        startTime = System.nanoTime();

        File serialisedTree = new File("%s.rtree".formatted(Files.getNameWithoutExtension(filename)));
        if (serialisedTree.exists()) {
            readTreeFromDisk(serialisedTree);
        } else {
            intersectTree = RTree.create();
            for (Entry entry : map.getTree().entries().toBlocking().toIterable()) {
                if (entry.geometry() instanceof Line) {
                    segments.add((Line) entry.geometry());
                }
            }
            for (Line l1 : segments) {
                if(!found.contains(l1)) {
                    for (Line l2 : segments) {
                        if (l1 != l2 && l1.intersects(l2) && !found.contains(l2)) {
                            for (Line l3 : segments) {
                                if ((l1 != l3) && (l2 != l3) && l1.intersects(l2) && l2.intersects(l3) && !found.contains(l3)) {
                                    try {
                                        Point2D insertion = Fitness.intersection(new Point2D.Double(l1.x1(), l1.y1()), new Point2D.Double(l1.x2(), l1.y2()), new Point2D.Double(l2.x1(), l2.y1()), new Point2D.Double(l2.x2(), l2.y2()));
                                        toInsert = Geometries.point(insertion.getX(), insertion.getY());
                                        intersectTree = intersectTree.add(String.valueOf(l1.hashCode() * l2.hashCode() * l3.hashCode()), toInsert);
                                        logger.info("Found intersection" + count);
                                        count++;
                                        found.add(l1);
                                        found.add(l2);
                                        found.add(l3);
                                        logger.info(String.valueOf(segments.size() - found.size()));
                                    } catch (IllegalArgumentException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                }
            }
            saveTreeToDisk(filename);
        }
    }

    /**
     * Save the currently in-memory R*-tree representation of the OpenStreetMap to a file
     *
     * @param filename the filename to which to serialise the R*-tree
     * @throws IOException if the filename provided is inaccessible or an error occurs during the write process
     */
    private void saveTreeToDisk(String filename) throws IOException {
        File serialisedTree = new File("%s.rtree".formatted(Files.getNameWithoutExtension(filename)));

        logger.info("Writing R*-tree to file %s".formatted(serialisedTree.getPath()));

        OutputStream outputStream = new FileOutputStream(serialisedTree);

        serializer.write(intersectTree, outputStream);

        logger.info("Tree save complete");
    }

    /**
     * De-serialise a previously saved R*-tree representing the desired OpenStreetMap
     *
     * @param serialisedTree reference to File where the serialised R*-tree is stored
     * @throws IOException if no such file exists or an error occurs during the import process
     */
    private void readTreeFromDisk(File serialisedTree) throws IOException {
        logger.info("Reading R*-tree from file %s".formatted(serialisedTree.getPath()));

        InputStream input = Files.asByteSource(serialisedTree).openStream();

        intersectTree = serializer.read(input, serialisedTree.length(), InternalStructure.DEFAULT);

        logger.info("Tree import complete");
    }

    //Perhaps what we need is to pregenerate an rTree of intersections this could significantly speed things up
    public List<Point> getPointList() throws IOException {
        makeTree(filename);
        PathIterator iterator = shape.getPathIterator(transform);
        double[] location = new double[6];
        double x, y;
        while (!iterator.isDone()) {
            iterator.currentSegment(location);
            x = location[0];
            y = location[1];

            Entry<String, Geometry> nearest = intersectTree
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
