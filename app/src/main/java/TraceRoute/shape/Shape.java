package TraceRoute.shape;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.List;

public class Shape {

    private Path2D path = new Path2D.Double();

    public Shape(List<Point2D> point2DList) {
        Point2D startPoint = point2DList.get(0);
        path.moveTo(startPoint.getX(), startPoint.getY());
        point2DList.remove(0);

        for (Point2D point :
                point2DList) {
            path.lineTo(point.getX(), point.getY());
        }
    }

    public Path2D getPath() {
        return path;
    }
}
