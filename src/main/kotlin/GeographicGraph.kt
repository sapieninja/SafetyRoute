import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Geometry
import com.wolt.osm.parallelpbf.entity.Node
import org.json.JSONArray
import java.io.File
import kotlin.math.pow

/**
 * Basic graph implementation, with some extra features to make things easier.
 * An R*Tree to make lookup more efficient
 * The implementation of various optimisation techniques to make routefinding faster
 * Ability to pull in Graph Weights from surrounding accident data.
 *
 */
class GeographicGraph {
    private val vertices : HashMap<Long,GeographicNode> = HashMap()
    var nodeTree: RTree<GeographicNode, Geometry> = RTree.star().maxChildren(30).create<GeographicNode, Geometry>()
    var wayTree : RTree<GeographicWay,Geometry> = RTree.star().maxChildren(30).create<GeographicWay,Geometry>()
    class GeographicNode (val longitude: Double, val latitude: Double)
    {
        var weight : Int = 0
        val connections = mutableSetOf<GeographicNode>()
    }
    class GeographicWay (val first: GeographicNode, val second: GeographicNode)

    /**
     * Takes in the OSM node ids, and adds the relevant edges between them
     */
    public fun addEdge(first :Long, second: Long)
    {
        val geographicFirst = vertices[first]
        val geographicSecond = vertices[second]
        if (geographicFirst != null && geographicSecond!= null) {
            geographicFirst.connections.add(geographicSecond)
            geographicSecond.connections.add(geographicFirst)
            wayTree = wayTree.add(GeographicWay(geographicFirst,geographicSecond),
                Geometries.line(geographicFirst.longitude,geographicFirst.latitude,geographicSecond.longitude,geographicSecond.latitude))
        }
    }

    /**
     * Adds a new node to the system and puts it in the R-TREE
     */
    public fun addNode(node : Node)
    {
        if(!vertices.containsKey(node.id)) {
            vertices[node.id] = GeographicNode(node.lon, node.lat)
            nodeTree = nodeTree.add(vertices[node.id], Geometries.point(node.lon,node.lat))
        }
    }

    /**
     * This function reads in the accident data, then adds it as a weight to all nodes.
     */
    public fun gatherWeights()
    {
        var accidentFile : File = File("output.json")
        var accidents = JSONArray(accidentFile.inputStream().readBytes().toString(Charsets.UTF_8))
        var counter = 0;
        for (accident in accidents)
        {
            counter += 1
            if(counter%100 == 0)
            {
                print(counter)
            }
            var parsedAccident = accident.toString().split(",")//hacky but will work
            var latitude = parsedAccident[0].substring(1).toDouble()
            var longitude = parsedAccident[1].toDouble()
            var severity = parsedAccident[2].substring(1,parsedAccident[2].length -2)
            var accidentWayObserver = wayTree.nearest(Geometries.point(longitude,latitude),100.0,1)
            if(accidentWayObserver.first() != null)
            {
               var accidentWay = accidentWayObserver.first().value()
                var additionNode = accidentWay.first
                if ((accidentWay.first.latitude-latitude).pow(2) + (accidentWay.first.longitude-longitude).pow(2) > (accidentWay.second.latitude-latitude).pow(2) + (accidentWay.second.longitude-longitude).pow(2))
                {
                    var additionNode = accidentWay.first
                }
                else
                {
                    var additionNode = accidentWay.second
                }
                when(severity)
                {
                    "slight" -> additionNode.weight += 1
                    "severe" -> additionNode.weight += 2
                    "fatal"  -> additionNode.weight += 3
                }
            }
        }
    }
}