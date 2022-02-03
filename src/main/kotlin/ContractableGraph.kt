import kotlinx.serialization.Serializable
import java.lang.Long.max
import java.lang.Long.min
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

//TODO allow routing with a passed in inputGraph
@Serializable
class ContractableGraph(var distanceCost : Double,var  turnCost: Double){
    /**
     * In the initalisation phase we create a new edge to edge graph based on path costs from the previous graph
     */
    var edgeLookUp = HashMap<Long,HashSet<String>>()
    var edgeVertices = HashMap<String,edgeVertice>()
    var noShortcuts = 0

    /**
     * A vertice that represents two connected edges and their associated connections (given as id's)
     */
    @Serializable
    class edgeVertice(val first : Long, val second: Long)
    {
        var deleted = false
        var hierachy = 0
        var connections : HashMap<String,Double> = HashMap<String, Double>()
        var incomingConnections = HashMap<String,Double>()
        var shortcutConnections = HashMap<String, Double>() //gives the weights of a shortcut based connection to another node
        var incomingShortcuts = HashMap<String,Double>()
        var shortcutRoutes = HashMap<String, String>() //gives the node through which the shortcut travels
    }

    class Tuple(val id : String, val dist : Int) : Comparable<Tuple>
    {
        override fun compareTo(other: Tuple): Int {
            if(dist > other.dist)
            {
                return 1
            }
            else if(dist == other.dist)
            {
                return 0
            }
            else
            {
                return -1
            }
        }
    }

    /**
     * Finds all edges
     * Checks every edge to see if it is reversible, if so it is added twice
     */
    //TODO edges are not symetrical i.e node1 to node2 is different from node2 to node1 so use the edgelookup system to fix this
    fun createGraph(inputGraph : GeographicGraph)
    {
        println("Creating Graph")
        var count = 0
       var edgeNo = 1
        for (item in inputGraph.vertices) edgeLookUp[item.key] = HashSet()
        for (start in inputGraph.vertices) {
            for(middle in start.value.connections)
            {
                for (end in inputGraph.vertices[middle]!!.connections)
                {
                    if(start.key!=end)
                    {
                        var cost = getEdgeCost(inputGraph,start.key,middle,end)
                        var idOne = (min(start.key,middle).toString()+"/"+max(start.key,middle).toString())
                        var idTwo = (min(middle,end).toString()+"/"+max(middle,end).toString())
                        if (!edgeVertices.containsKey(idOne)) {
                            var one = edgeVertice(start.key, middle)
                            edgeVertices[idOne] = one
                            one.connections[idTwo] = cost
                        }
                        else{
                            var one = edgeVertices[idOne]
                            one!!.connections[idTwo] = cost
                        }
                        if (!edgeVertices.containsKey(idTwo)) {
                            var two = edgeVertice(middle, end)
                            edgeVertices[idTwo] = two
                            two.incomingConnections[idOne] = cost
                        }
                        else{
                            var two = edgeVertices[idTwo]
                            two!!.incomingConnections[idOne] = cost
                        }
                        edgeLookUp[start.key]!!.add(idOne)
                        edgeLookUp[middle]!!.add(idOne)
                        edgeLookUp[middle]!!.add(idTwo)
                        edgeLookUp[end]!!.add(idTwo)
                    }
                }
            }
        }
    }

    /**
     * Finds the route using contraction hierachies.
     * First the set of nodes with a higher hierachy from the from node are calculated
     * Then the set of nodes with a higher hierachy from the to node (backwards) are calculated
     * Then the intersection of the set of settled nodes is found and the minimum found.
     * Then the route is created by recursively looking up the shortcuts
     */
    fun findRoute(from : Long, to : Long, inputGraph: GeographicGraph)
    {
        class Tuple(val id : String, val dist : Double) : Comparable<Tuple>
        {
            override fun compareTo(other: Tuple): Int {
                if(dist > other.dist)
                {
                    return 1
                }
                else if(dist == other.dist)
                {
                    return 0
                }
                else
                {
                    return -1
                }
            }
        }
        var F = PriorityQueue<Tuple>()
        var settledFrom = HashSet<String>()
        var dist = HashMap<String, Double>()
        var prev = HashMap<String, String>()
        var u : String
        for (i in edgeVertices.keys) {
            prev[i] = "-1"
            dist[i] = Double.MAX_VALUE
        }
        for (startEdge in edgeLookUp[from]!!) {
            dist[startEdge] = inputGraph.getDistance(edgeVertices[startEdge]!!.first,edgeVertices[startEdge]!!.second) *distanceCost *1/2
            prev[startEdge] = "no"
            val toAdd = Tuple(startEdge, 0.0)
            F.add(toAdd)
        }
        while (F.size != 0)
        {
            u = F.poll().id
            settledFrom.add(u)
            for (neighbour in edgeVertices[u]?.connections!!) {
                if (edgeVertices[neighbour.key]!!.hierachy < edgeVertices[u]!!.hierachy) continue
                var alt = neighbour.value + dist[u]!!
                if (alt != null) {
                    if (alt < dist[neighbour.key]!!) {
                        dist[neighbour.key] = alt
                        prev[neighbour.key] = u
                        val toAdd = Tuple(neighbour.key, dist[neighbour.key]!!)
                        F.add(toAdd)
                    }
                }
            }
            for (neighbour in edgeVertices[u]?.shortcutConnections!!) {
                if (edgeVertices[neighbour.key]!!.hierachy < edgeVertices[u]!!.hierachy) continue
                var alt = neighbour.value + dist[u]!!
                if (alt != null) {
                    if (alt < dist[neighbour.key]!!) {
                        dist[neighbour.key] = alt
                        prev[neighbour.key] = u
                        val toAdd = Tuple(neighbour.key, dist[neighbour.key]!!)
                        F.add(toAdd)
                    }
                }
            }
        }
        F = PriorityQueue<Tuple>()
        var settledTo = HashSet<String>()
        var distTo = HashMap<String, Double>()
        var prevTo = HashMap<String, String>()
        for (i in edgeVertices.keys) {
            prevTo[i] = "-1"
            distTo[i] =  Double.MAX_VALUE
        }
        for (startEdge in edgeLookUp[to]!!) {
            distTo[startEdge] = inputGraph.getDistance(edgeVertices[startEdge]!!.first,edgeVertices[startEdge]!!.second) *distanceCost *1/2
            prevTo[startEdge] = "no"
            val toAdd = Tuple(startEdge, 0.0)
            F.add(toAdd)
        }
        while (F.size != 0)
        {
            u = F.poll().id
            settledTo.add(u)
            for (neighbour in edgeVertices[u]?.incomingConnections!!) {
                if (edgeVertices[neighbour.key]!!.hierachy < edgeVertices[u]!!.hierachy) continue
                var alt = neighbour.value + distTo[u]!!
                if (alt != null) {
                    if (alt < distTo[neighbour.key]!!) {
                        distTo[neighbour.key] = alt
                        prevTo[neighbour.key] = u
                        val toAdd = Tuple(neighbour.key, distTo[neighbour.key]!!)
                        F.add(toAdd)
                    }
                }
            }
            for (neighbour in edgeVertices[u]?.incomingShortcuts!!) {
                if (edgeVertices[neighbour.key]!!.hierachy < edgeVertices[u]!!.hierachy) continue
                var alt = neighbour.value + distTo[u]!!
                if (alt != null) {
                    if (alt < distTo[neighbour.key]!!) {
                        distTo[neighbour.key] = alt
                        prevTo[neighbour.key] = u
                        val toAdd = Tuple(neighbour.key, distTo[neighbour.key]!!)
                        F.add(toAdd)
                    }
                }
            }
        }
        println(settledTo.intersect(settledFrom))
        for (i in settledTo.intersect(settledFrom))
        {
            println(dist[i]!!+distTo[i]!!)
        }
    }
    /**
     * Creates another graph: G*, which is contracted
     * Uses a edge change heuristic, which aims to minimise the number of edges reduced
     */
    fun contractGraph()
    {
        println("Contracting Graph")
        var current = 1
        var contractionQueue = PriorityQueue<Tuple>()
        for (vertice in edgeVertices.keys)
        {
            contractionQueue.add(Tuple(vertice,getEdgeDifference(vertice)))
            println(contractionQueue.size)
        }
        while (contractionQueue.size != 0)
        {
            var next = contractionQueue.poll()
            var oldEdges = next.dist.toInt()
            var newEdges = getEdgeDifference(next.id)
            if (oldEdges != newEdges) {
                contractionQueue.add(Tuple(next.id,newEdges))
                continue
            }
            contractNode(next.id,current)
            current += 1
            println(contractionQueue.size)
            println(newEdges)
        }
    }

    /**
     * Performs the act of contraction on the input node
     */
    private fun contractNode(node : String, current : Int)
    {
        var nodeObj = edgeVertices[node]!!
        for (from in nodeObj.incomingConnections.keys.union(nodeObj.incomingShortcuts.keys))
        {
            for (to in nodeObj.connections.keys.union(nodeObj.shortcutConnections.keys))
            {
                if(from!=to)
                {
                    val fromVertice = edgeVertices[from]!!
                    val toVertice = edgeVertices[to]!!
                    if (fromVertice.deleted or toVertice.deleted) continue
                    if (isShortest(from,node,to))
                    {
                        noShortcuts += 1
                        var connectionWeight = 0.0
                        if (fromVertice.connections.containsKey(node)) connectionWeight += fromVertice.connections[node]!!
                        else if (fromVertice.shortcutConnections.containsKey(node)) connectionWeight += fromVertice.shortcutConnections[node]!!
                        if (nodeObj.connections.containsKey(to)) connectionWeight += nodeObj.connections[to]!!
                        else if (nodeObj.shortcutConnections.containsKey(to)) connectionWeight += nodeObj.shortcutConnections[to]!!
                        fromVertice.shortcutConnections[to] = connectionWeight
                        fromVertice.shortcutRoutes[to] = node
                        toVertice.incomingShortcuts[from]=connectionWeight
                    }
                }
            }
        }
        nodeObj.deleted = true
        nodeObj.hierachy= current
    }

    /**
     * Works out the edge difference
     * This is equivalent to the number of shortcuts that would be added if the node were to be deleted
     * or the number of the routes between adjacent nodes that pass through this node.
     */
    private fun getEdgeDifference(node : String) : Int
    {
        var nodeObj = edgeVertices[node]!!
        var count = 0
        for (from in nodeObj.incomingConnections.keys.union(nodeObj.incomingShortcuts.keys))
        {
                for (to in nodeObj.connections.keys.union(nodeObj.shortcutConnections.keys))
            {
                if(from!=to)
                {
                    val fromVertice = edgeVertices[from]!!
                    val toVertice = edgeVertices[to]!!
                    if (fromVertice.deleted or toVertice.deleted) continue
                    if (isShortest(from,node,to)) count += 1
                }
            }
        }
        return count
    }

    /**
     * Just a standard dijkstra implementation
     * TOOD make this use the infinite graph variant because that will be faster
     */
    private fun isShortest(from: String, by : String, to : String) : Boolean
    {
        class Tuple(val id : String, val dist : Double) : Comparable<Tuple>
        {
            override fun compareTo(other: Tuple): Int {
                if(dist > other.dist)
                {
                    return 1
                }
                else if(dist == other.dist)
                {
                    return 0
                }
                else
                {
                    return -1
                }
            }
        }
        val F = PriorityQueue<Tuple>()
        val dist = HashMap<String, Double>()
        val prev = HashMap<String, String>()
        dist[from] = 0.0
        var u : String
        for (i in edgeVertices.keys) {
            dist[i] = Double.MAX_VALUE
            prev[i] = "-1"
        }
        val toAdd = Tuple(from,0.0)
        F.add(toAdd)
        while (F.size != 0) {
            u = F.poll().id
            if (u == to) continue
            for (neighbour in edgeVertices[u]?.connections!!) {
                if (edgeVertices[neighbour.key]!!.deleted) continue
                var alt = neighbour.value + dist[u]!!
                if (alt != null) {
                    if (alt < dist[neighbour.key]!!) {
                        dist[neighbour.key] = alt
                        prev[neighbour.key] = u
                        val toAdd = Tuple(neighbour.key, dist[neighbour.key]!!)
                        F.add(toAdd)
                    }
                }
            }
            for (neighbour in edgeVertices[u]?.shortcutConnections!!) {
                if (edgeVertices[neighbour.key]!!.deleted) continue
                var alt = neighbour.value + dist[u]!!
                if (alt != null) {
                    if (alt < dist[neighbour.key]!!) {
                        dist[neighbour.key] = alt
                        prev[neighbour.key] = u
                        val toAdd = Tuple(neighbour.key, dist[neighbour.key]!!)
                    }
                }
            }
        }
        return prev[to] == by
    }
    /**
     * Gets the cost of moving between two edges for our new edge graph.
     */
    fun getEdgeCost(inputGraph: GeographicGraph,start: Long, middle : Long, end : Long): Double {
        var distance = (inputGraph.getDistance(start,middle) + inputGraph.getDistance(middle,end))/2
        var danger = inputGraph.vertices[middle]!!.weight
        var turning = inputGraph.getTurnCost(start,middle,end,turnCost)
        return distance * distanceCost + turning + danger
    }
}