package TraceRoute.ea;

import TraceRoute.osm.OpenStreetMap;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Conductor {
    private final List<Route> children;

    public Conductor(Shape shape, OpenStreetMap map) {
        children = new ArrayList<>(100);

        com.github.davidmoten.rtree.geometry.Rectangle mapBounds = map.getBounds();
        Rectangle2D shapeBounds = shape.getBounds2D();

        double maxScaleFactor = Math.min(
                Math.abs(mapBounds.x2() - mapBounds.x1()) / shapeBounds.getWidth(),
                Math.abs(mapBounds.y2() - mapBounds.y1()) / shapeBounds.getHeight()
        );

        for (int i = 0; i < 10; i++)
            children.add(new Route(
                    shape,
                    ThreadLocalRandom.current().nextDouble(maxScaleFactor / 20, maxScaleFactor),
                    new Point2D.Double(
                            ThreadLocalRandom.current().nextDouble(mapBounds.x1(), mapBounds.x2()),
                            ThreadLocalRandom.current().nextDouble(mapBounds.y1(), mapBounds.y2())
                    ), map)
            );
    }

    public Route findOptimalRoute() {
        children.sort(Comparator.comparing(Route::getFitness).reversed());
        return children.get(0);
    }
}
