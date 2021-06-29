package TraceRoute.fitness;

import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Geometry;

import java.awt.geom.Point2D;
import java.util.List;

public class Fitness {
    public static Double Perpendicular(RTree<String, Geometry> london, List<Point2D> pointslist)
    {
        Iterable<Entry<String,Geometry>> entries = london.search(Geometries.pointGeographic(51.4, 0.00), 100).toBlocking().toIterable();
        for (Entry entry:entries
             ) {
            System.out.println(entry);
        }
        return 0.0;
    }
}
