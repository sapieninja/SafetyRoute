import com.wolt.osm.parallelpbf.entity.Node

/**
 * Basic graph implementation, with some extra features to make things easier.
 * An R*Tree to make lookup more efficient
 * The implementation of various optimisation techniques to make routefinding faster
 * Ability to pull in Graph Weights from surrounding accident data.
 *
 */
class GeographicGraph {
    private val vertices : MutableSet<GeographicNode> = mutableSetOf()
    class GeographicNode (val longitude: Double, val latitude: Double,val id : Int)
    {
        val weight : Int = 0
        /**
         * By Overriding hashCode with the id we make sure that we don't create multiple nodes for one given
         * OSM node
         */
        override public fun hashCode(): Int {
            return this.id
        }
        val connections = setOf<GeographicNode>()
    }

    /**
     * Takes in the OSM nodes, and adds the relevant edges between them
     */
    public fun addEdge(first :Node, second: Node)
    {
    }
    public fun addNode(node : Node)
    {
       val toAdd = GeographicNode(node.lon,node.lat,node.id.toInt())
        vertices.add(toAdd)
        println(vertices.size)
    }
}