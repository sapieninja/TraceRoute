package TraceRoute.osm;

import com.github.davidmoten.rtree.InternalStructure;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.Serializer;
import com.github.davidmoten.rtree.Serializers;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Geometry;
import com.google.common.io.Files;
import com.wolt.osm.parallelpbf.ParallelBinaryParser;
import com.wolt.osm.parallelpbf.entity.Node;
import com.wolt.osm.parallelpbf.entity.Way;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.List;


/**
 * This class handles the importing and storage of OpenStreetMap data.
 */
@SuppressWarnings("UnstableApiUsage")
public class OpenStreetMap {
    /**
     * Lookup table for vertex ID -> all other node info
     */
    private final HashMap<Long, Node> vertices = new HashMap<>();
    /**
     * For timing how long the map import process takes to complete
     */
    private final long startTime;
    /**
     * Used for logging to the console instead of using <code>System.out.println</code>
     */
    private final Logger logger;
    private final Serializer<String, Geometry> serializer = Serializers.flatBuffers().utf8();
    /**
     * R*-tree to store the road network
     */
    private RTree<String, Geometry> tree = RTree.star().create();
    /**
     * Store the filename of the OpenStreetMap that this instance represents
     * This is used for saving and reading a processed version of the map
     */
    private final String filename;

    /**
     * Initialise a new data object into which to import a PBF file from the OpenStreetMap project
     *
     * @param filename a relative path to the PBF file (pwd = app)
     * @throws IOException if the file is not found an exception is thrown
     */
    public OpenStreetMap(String filename) throws IOException {

        logger = LoggerFactory.getLogger(OpenStreetMap.class);

        // the filename is used for saving the processed R*-tree
        this.filename = filename;

        startTime = System.nanoTime();

        File serialisedTree = new File("%s.rtree".formatted(Files.getNameWithoutExtension(filename)));

        if (serialisedTree.exists()) {
            logger.info("A previously saved R*-tree already exists at %s, skipping import step".formatted(serialisedTree.getPath()));

            readTreeFromDisk(serialisedTree);
        } else {
            File map = new File(filename);
            InputStream input = Files.asByteSource(map).openStream();

            logger.info("Starting map import");

            // Use Runtime.getRuntime().availableProcessors() for acc number of CPUs
            // But to do concurrent access to JGraphT requires special code
            // So we set the number of threads to 1
            new ParallelBinaryParser(input, 1)
                    .onNode(this::processNodes)
                    .onWay(this::processWays)
                    .onComplete(this::importComplete)
                    .parse();
        }
    }

    /**
     * This method is called by the PBF parser
     */
    private void importComplete() {
        long timeTaken = System.nanoTime() - startTime;
        logger.info("Map import complete in " + (float) timeTaken / 1000 / 1000 / 1000 + "s");

        try {
            saveTreeToDisk("%s.rtree".formatted(Files.getNameWithoutExtension(filename)));
        } catch (IOException e) {
            logger.error("Could not save processed R*-tree to disk", e);
        }
    }

    /**
     * This method is called by the PBF parser when a <code>way</code> element is encountered
     *
     * @param way in essence, a list of nodes that make up the way (i.e. street/road/alley)
     */
    private void processWays(Way way) {
        logger.trace("Processing way %d".formatted(way.getId()));

        List<Long> nodes = way.getNodes();
        Node lastNode = vertices.get(nodes.get(0));
        nodes.remove(0);
        for (Long nodeID : nodes) {
            Node newNode = vertices.get(nodeID);

            if (newNode == null) {
                logger.warn("Unknown vertex detected");
                continue;
            }

            tree = tree.add(String.valueOf(way.getId()), Geometries.line(lastNode.getLon(), lastNode.getLat(), newNode.getLon(), newNode.getLat()));

            lastNode = newNode;
        }
    }

    /**
     * This method is called by the PBF parser when a <code>node</code> element is encountered
     *
     * @param node a node is an element from which roads are formed in OSM
     */
    private void processNodes(Node node) {
        logger.trace("Processing node %d".formatted(node.getId()));

        vertices.put(node.getId(), node);

        // R*-tree implementation is immutable, so we reassign tree to output of add command
        tree = tree.add(String.valueOf(node.getId()), Geometries.point(node.getLon(), node.getLat()));
    }

    /**
     * Save the currently in-memory R*-tree representation of the OpenStreetMap to a file
     *
     * @param filename the filename to which to serialise the R*-tree
     * @throws IOException if the filename provided is inaccessible or an error occurs during the write process
     */
    private void saveTreeToDisk(String filename) throws IOException {
        File serialisedTree = new File(filename);

        logger.info("Writing R*-tree to file %s".formatted(serialisedTree.getPath()));

        OutputStream outputStream = new FileOutputStream(serialisedTree);

        serializer.write(tree, outputStream);

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

        tree = serializer.read(input, serialisedTree.length(), InternalStructure.DEFAULT);

        logger.info("Tree import complete");
    }

    @SuppressWarnings("unused")
    private void visualiseTree() {
        tree.visualize(5000, 5000).save("build/r-tree.png");
    }
}
