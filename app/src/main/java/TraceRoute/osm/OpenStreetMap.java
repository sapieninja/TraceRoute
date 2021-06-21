package TraceRoute.osm;

import com.google.common.io.Files;
import com.wolt.osm.parallelpbf.ParallelBinaryParser;
import com.wolt.osm.parallelpbf.entity.*;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;


public class OpenStreetMap {
    private final Graph<Long, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
    private final HashMap<Long, Node> vertices = new HashMap<>();
    private final long startTime;

    public OpenStreetMap(String filename) throws IOException {

        startTime = System.nanoTime();

        try {
            File map = new File(filename);
            InputStream input = Files.asByteSource(map).openStream();

            // Use Runtime.getRuntime().availableProcessors() for acc number of CPUs
            // But to do concurrent access to JGraphT requires special code
            // So we set the number of threads to 1
            new ParallelBinaryParser(input, 1)
                    .onHeader(this::processHeader)
                    .onBoundBox(this::processBoundingBox)
                    .onNode(this::processNodes)
                    .onWay(this::processWays)
                    .onRelation(this::processRelations)
                    .onChangeset(this::processChangeSets)
                    .onComplete(this::importComplete)
                    .parse();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void importComplete() {
        long timeTaken = System.nanoTime() - startTime;
        System.out.println("Map import complete in " + (float) timeTaken / 1000 / 1000 / 1000 + "s");
    }

    /**
     * This method is called by the PBF parser when a <code>way</code> element is encountered
     * @param way in essence, a list of nodes that make up the way (i.e. street/road/alley)
     */
    private void processWays(Way way) {
        List<Long> nodes = way.getNodes();
        Long last = nodes.get(0);
        nodes.remove(0);
        for (Long nodeID : nodes) {
            graph.addEdge(last, nodeID);
            last = nodeID;
        }
    }

    private void processNodes(Node node) {
        graph.addVertex(node.getId());
        vertices.put(node.getId(), node);
    }

    /* The below functions could be used...
    but probably don't need them four our parsing purposes */

    private void processHeader(Header header) {
    }

    private void processBoundingBox(BoundBox boundBox) {
    }

    private void processChangeSets(Long aLong) {
    }

    private void processRelations(Relation relation) {
    }
}
