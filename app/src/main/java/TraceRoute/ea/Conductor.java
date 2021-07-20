package TraceRoute.ea;

import TraceRoute.osm.OpenStreetMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

public class Conductor {
    private final OpenStreetMap map;
    private final Shape shape;
    private final Logger logger;
    double maxScaleFactor;
    private List<Route> children;
    private BlockingQueue<List<Double>> childqueue;
    private final ExecutorService executor;
    private LinkedList<Callable<Integer>> toRun;

    public Conductor(Shape shape, OpenStreetMap map) {
        children = new ArrayList<Route>(1000);
        toRun = new LinkedList<Callable<Integer>>();
        childqueue = new LinkedBlockingQueue<>();
        logger = LoggerFactory.getLogger(Route.class);
        this.map = map;
        this.shape = shape;
        int p = 0;
        com.github.davidmoten.rtree.geometry.Rectangle mapBounds = map.getBounds();
        Rectangle2D shapeBounds = shape.getBounds2D();

        maxScaleFactor = Math.min(
                Math.abs(mapBounds.x2() - mapBounds.x1()) / shapeBounds.getWidth(),
                Math.abs(mapBounds.y2() - mapBounds.y1()) / shapeBounds.getHeight()
        );
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (int i = 0; i < 1000; i++) {
            toRun.add(this::generateRoute);
        }
        try {
            executor.invokeAll(toRun);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    private int generateRoute() {
        if(childqueue.isEmpty()) {
            children.add(new Route(
                    shape,
                    ThreadLocalRandom.current().nextDouble(maxScaleFactor / 20, maxScaleFactor/4),
                    new Point2D.Double(
                            ThreadLocalRandom.current().nextDouble(-0.3, 0.2),
                            ThreadLocalRandom.current().nextDouble(51.4, 51.6)
                    ),
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    map)
            );
        }
        else
        {
            logger.info(String.valueOf(childqueue.size()));
            try {
                List<Double> child = childqueue.poll(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

                children.add(new Route(
                        shape,
                        child.get(0),
                        new Point2D.Double(child.get(1), child.get(2)),
                        child.get(3),
                        child.get(4),
                        child.get(5),
                        child.get(6),
                        map)
                );
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        return 0;
    }

    public Route findOptimalRoute() {
        double prevFitness;
        children.sort(Comparator.comparing(Route::getFitness));
        try {
            for (int x = 0; x < 200; x++) {
                toRun = new LinkedList<Callable<Integer>>();
                logger.info("Beginning " + x + " Generation " + children.get(0).getFitness());
                prevFitness = children.get(0).getFitness();
                children = children.subList(0,200);
                for (int i = 0; i < 200; i++) {
                    for (int q = 0; q < 3; q++) {
                        List<Double> child = children.get(i).getChild(1.0, 0.5);
                        if(Math.abs(child.get(0))>maxScaleFactor/2 || Math.abs(child.get(0))>maxScaleFactor/10)
                        {
                            child.set(0,maxScaleFactor/2);
                        }
                        childqueue.offer(child,Long.MAX_VALUE,TimeUnit.NANOSECONDS);
                        toRun.add(this::generateRoute);
                    }
                    for(int g = 0; g < 1; g++) {
                        toRun.add(this::generateRoute); //Adds some new random ones to see if they are better
                    }
                }
                try {
                    executor.invokeAll(toRun);
                    System.out.println();
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
                children.sort(Comparator.comparing(Route::getFitness));
                if(children.get(0).getFitness() == prevFitness)
                {
                    break;
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        children.sort(Comparator.comparing(Route::getFitness));
        return children.get(0);
    }
}
