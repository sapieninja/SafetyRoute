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
    var edgeLookUp = HashMap<Long,HashSet<Long>>()
    var edgeVertices = HashMap<Long,edgeVertice>()
    var noShortcuts = 0

    /**
     * A vertice that represents two connected edges and their associated connections (given as id's)
     */
    @Serializable
    class edgeVertice(val first : Long, val second: Long)
    {
        var deleted = false
        var hierachy = 0
        var connections = HashMap<Long, Double>()
        var incomingConnections = HashMap<Long,Double>()
        var shortcutConnections = HashMap<Long, Double>() //gives the weights of a shortcut based connection to another node
        var incomingShortcuts = HashMap<Long,Double>()
        var shortcutRoutes = HashMap<Long, Long>() //gives the node through which the shortcut travels
    }

    class Tuple(val id : Long, val dist : Int) : Comparable<Tuple>
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
        var count : Long = 0
       var edgeNo = 1
        for (item in inputGraph.vertices) edgeLookUp[item.key] = HashSet()
        for (start in inputGraph.vertices) {
            for(middle in start.value.connections)
            {
                for (end in inputGraph.vertices[middle]!!.connections)
                {
                    if(start.key!=end)
                    {
                        count += 1
                        var cost = getEdgeCost(inputGraph,start.key,middle,end)
                        var idOne = count
                        if (edgeLookUp[start.key]!!.intersect(edgeLookUp[middle]!!).size == 1) idOne = edgeLookUp[start.key]!!.intersect(edgeLookUp[middle]!!).first()
                        count += 1
                        var idTwo = count
                        if (edgeLookUp[middle]!!.intersect(edgeLookUp[end]!!).size == 1) idTwo = edgeLookUp[middle]!!.intersect(edgeLookUp[end]!!).first()
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
    fun findRoute(from : Long, to : Long, inputGraph: GeographicGraph): MutableList<Long> {
        println("Using contracted Graph")
        class Tuple(val id : Long, val dist : Double) : Comparable<Tuple>
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
        var settledFrom = HashSet<Long>()
        var dist = HashMap<Long, Double>()
        var prev = HashMap<Long, Long>()
        var u : Long
        for (startEdge in edgeLookUp[from]!!) {
            dist[startEdge] = inputGraph.getDistance(edgeVertices[startEdge]!!.first,edgeVertices[startEdge]!!.second) *distanceCost *1/2
            prev[startEdge] = -1
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
                    if (!dist.containsKey(neighbour.key)|| alt< dist[neighbour.key]!!) {
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
                    if (!dist.containsKey(neighbour.key)|| alt< dist[neighbour.key]!!) {
                        dist[neighbour.key] = alt
                        prev[neighbour.key] = u
                        val toAdd = Tuple(neighbour.key, dist[neighbour.key]!!)
                        F.add(toAdd)
                    }
                }
            }
        }
        F = PriorityQueue<Tuple>()
        var settledTo = HashSet<Long>()
        var distTo = HashMap<Long, Double>()
        var prevTo = HashMap<Long, Long>()
        for (startEdge in edgeLookUp[to]!!) {
            distTo[startEdge] = inputGraph.getDistance(edgeVertices[startEdge]!!.first,edgeVertices[startEdge]!!.second)*distanceCost *1/2
            prevTo[startEdge] = -1
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
                    if (!distTo.containsKey(neighbour.key)|| alt< distTo[neighbour.key]!!) {
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
                    if (!distTo.containsKey(neighbour.key)|| alt< distTo[neighbour.key]!!) {
                        distTo[neighbour.key] = alt
                        prevTo[neighbour.key] = u
                        val toAdd = Tuple(neighbour.key, distTo[neighbour.key]!!)
                        F.add(toAdd)
                    }
                }
            }
        }
        var minimumNode : Long = -1
        var minimumCost = Double.MAX_VALUE
        for (i in settledTo.intersect(settledFrom))
        {
            if (dist[i]!! + distTo[i]!! < minimumCost)
            {
                minimumNode = i
                minimumCost = dist[i]!! + distTo[i]!!
            }
        }
        return convertToNodeRoute(from,deContractRoute(from,minimumNode,to,prev,prevTo),to)
    }

    /**
     * With given conditions, decontracts the route
     */
    fun deContractRoute(from: Long, minimumNode : Long, to : Long, prev : HashMap<Long,Long>, prevTo: HashMap<Long,Long>): MutableList<Long> {
        var route = mutableListOf<Long>(minimumNode)
        while (prev[route[0]]!! != -1L) {
            route.add(0, prev[route[0]]!!)
        }
        while (prevTo[route.last()]!! != -1L) {
            route.add(prevTo[route.last()]!!)
        }
        return unpackRoute(route)
    }
    fun unpackRoute(route : MutableList<Long>): MutableList<Long> {
        while (true) {
            var finished = true
            for (i in 1..(route.size - 1)) {
                var current = edgeVertices[route[i - 1]]!!
                if (current.shortcutConnections.containsKey(route[i])) {
                    route.add(i, current.shortcutRoutes[route[i]]!!)
                    finished = false
                    break
                }
            }
            if (finished) break
        }
        return route
    }
    fun convertToNodeRoute(from: Long, route : MutableList<Long>, to : Long): MutableList<Long> {
        var nodeRoute = mutableListOf<Long>()
        nodeRoute.add(from)
        for (edgeVertice in route) {
            var verticeObj = edgeVertices[edgeVertice]!!
            if (nodeRoute.last() == verticeObj.first) nodeRoute.add(verticeObj.second)
            else nodeRoute.add(verticeObj.first)
        }
        nodeRoute.add(to)
        return nodeRoute
    }

    /**
     * Creates another graph: G*, which is contracted
     * Uses a edge change heuristic, which aims to minimise the number of edges reduced
     */
    fun contractGraph()
    {
        println("Contracting Graph of size ${edgeVertices.size}")
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
        }
    }

    /**
     * Performs the act of contraction on the input node
     */
    private fun contractNode(node : Long, current : Int)
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
    private fun getEdgeDifference(node : Long) : Int
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

    fun getIllegal(from: Long, by: Long, to: Long, prev : HashMap<Long,Long>) : HashSet<Long>
    {
        var illegalNeighbours = HashSet<Long>()
        var route = deContractRoute(from,by,to,prev,HashMap<Long,Long>())
        for (edgeVertice in route)
        {
            if (edgeVertice != to)
            {
                illegalNeighbours = illegalNeighbours.union(edgeVertices[edgeVertice]!!.connections.keys) as HashSet<Long>
            }
        }
        return illegalNeighbours
    }
    /**
     * Just a standard dijkstra implementation
     */
    private fun isShortest(from: Long, by : Long, to : Long) : Boolean
    {
        class Tuple(val id : Long, val dist : Double) : Comparable<Tuple>
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
        val dist = HashMap<Long, Double>()
        val prev = HashMap<Long, Long>()
        dist[from] = 0.0
        var u : Long
        val toAdd = Tuple(from,0.0)
        prev[from] = -1
        F.add(toAdd)
        var numSettled = 0
        while (F.size != 0) {
            u = F.poll().id
            var toIgnore = getIllegal(from,prev[u]!!,u,prev)
            numSettled += 1
            if (u == to) break
            for (neighbour in edgeVertices[u]?.connections!!) {
                if (edgeVertices[neighbour.key]!!.deleted) continue
                var alt = neighbour.value + dist[u]!!
                if ((toIgnore.intersect(unpackRoute(listOf<Long>(u,neighbour.key) as MutableList<Long>).toSet())).size > 1) continue
                if (alt != null) {
                    if (!dist.containsKey(neighbour.key)||alt < dist[neighbour.key]!!) {
                        dist[neighbour.key] = alt
                        prev[neighbour.key] = u
                        val toAdd = Tuple(neighbour.key, dist[neighbour.key]!!)
                        F.add(toAdd)
                    }
                }
            }
            for (neighbour in edgeVertices[u]?.shortcutConnections!!) {
                if (edgeVertices[neighbour.key]!!.deleted) continue
                if ((toIgnore.intersect(unpackRoute(listOf<Long>(u,neighbour.key) as MutableList<Long>).toSet())).size > 1) continue
                var alt = neighbour.value + dist[u]!!
                if (alt != null) {
                    if (!dist.containsKey(neighbour.key) || alt < dist[neighbour.key]!!) {
                        dist[neighbour.key] = alt
                        prev[neighbour.key] = u
                        val toAdd = Tuple(neighbour.key, dist[neighbour.key]!!)
                        F.add(toAdd)
                    }
                }
            }
        }
        if(numSettled>20000){
            println("$numSettled,${prev[to]},${dist[to]}")
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