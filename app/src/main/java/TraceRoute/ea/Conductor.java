package TraceRoute.ea;

import TraceRoute.osm.OpenStreetMap;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Conductor {
    private final List<Route> children;

    private final OpenStreetMap map;

    private final Shape shape;

    double maxScaleFactor;

    private final ExecutorService executor;

    public Conductor(Shape shape, OpenStreetMap map) {
        children = new ArrayList<>(100);
        this.map = map;
        this.shape = shape;

        com.github.davidmoten.rtree.geometry.Rectangle mapBounds = map.getBounds();
        Rectangle2D shapeBounds = shape.getBounds2D();

        maxScaleFactor = Math.min(
                Math.abs(mapBounds.x2() - mapBounds.x1()) / shapeBounds.getWidth(),
                Math.abs(mapBounds.y2() - mapBounds.y1()) / shapeBounds.getHeight()
        );

        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < 20; i++) executor.submit(this::generateRoute);
    }

    private void generateRoute() {
        children.add(new Route(
                shape,
                ThreadLocalRandom.current().nextDouble(maxScaleFactor / 3, maxScaleFactor),
                new Point2D.Double(
                        ThreadLocalRandom.current().nextDouble(-0.3, 0.1),
                        ThreadLocalRandom.current().nextDouble(51.3, 51.6)
                ), map)
        );
    }

    public Route findOptimalRoute() {
        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            children.sort(Comparator.comparing(Route::getFitness).reversed());
            return children.get(0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}
