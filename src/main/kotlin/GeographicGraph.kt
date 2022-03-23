import com.github.davidmoten.rtree2.Entries
import com.github.davidmoten.rtree2.Entry
import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Geometry
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.json.JSONArray
import java.io.File
import java.math.BigDecimal
import java.security.InvalidParameterException
import java.util.*
import kotlin.NoSuchElementException
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.*


/**
 * Basic graph implementation, with some extra features to make things easier.
 * An R*Tree to make lookup more efficient
 * The implementation of various optimisation techniques to make routefinding faster
 * Ability to pull in Graph Weights from surrounding accident data.
 *
 */
@Serializable
class GeographicGraph {
    var vertices: HashMap<Long, GeographicNode> = HashMap()
    var safeNodes = HashSet<Long>() //nodes that are known to be safe
    var slowNodes = HashSet<Long>()
    var contractedGraphs = HashMap<Double, ContractableGraph>()

    @Transient
    var nodeTree: RTree<Long, Geometry> = RTree.star().maxChildren(30).create()

    @Serializable
    class GeographicNode(val longitude: Double, val latitude: Double) {
        var weight: Double = 0.0
        var connections = HashSet<Long>()
        var incomingConnections = HashSet<Long>()
    }

    //This would be in the init method but some things are configured externally first
    fun setupRTree() {
        if (nodeTree.size() != 0) {
            return
        }
        var nodeList = mutableListOf<Entry<Long, Geometry>>()
        for (node in vertices) {
            nodeList.add(Entries.entry(node.key, Geometries.point(node.value.longitude, node.value.latitude)))
        }
        nodeTree = RTree.star().maxChildren(28).create(nodeList)
    }

    fun getRandomId(): Long {
        return vertices.keys.toList()[Random().nextInt(vertices.size)]
    }

    fun addEdge(first: Long, second: Long, oneWay: Boolean) {
        val geographicFirst = vertices[first]
        val geographicSecond = vertices[second]
        if (geographicFirst != null && geographicSecond != null) {
            vertices[first]?.connections?.add(second)
            vertices[second]?.incomingConnections?.add(first)
            if (!oneWay) {
                vertices[second]?.connections?.add(first)
                vertices[first]?.incomingConnections?.add(second)
            }
        }
    }

    /**
     * This function reads in the accident data, then adds it as a  weight to all nodes.
     */
    fun gatherWeights() {
        val accidentFile = File("output.json")
        val accidents = JSONArray(accidentFile.inputStream().readBytes().toString(Charsets.UTF_8))
        for (accident  in accidents) {
            val parsedAccident = accident as JSONArray
            val latitude: Double = (parsedAccident[0] as BigDecimal).toDouble()
            val longitude: Double = (parsedAccident[1] as BigDecimal).toDouble()
            val severity = parsedAccident[2] as String
            try {
                val additionNode = vertices[getNearestNode(latitude, longitude)]!!
                when (severity) {
                    "Slight" -> additionNode.weight += 1
                    "Severe" -> additionNode.weight += 2
                    "Fatal" -> additionNode.weight += 3
                }
            } catch (e: InvalidParameterException) {
                //In this case we can still apply all the other accidents, even if this accident doesn't match well.
                continue
            }
        }
    }

    /**
     * This function takes in a latitude and longitude,
     * and uses these to find the id of the nearest node
     */
    private fun getNearestNode(latitude: Double, longitude: Double): Long {
        val closestNodeObserver = nodeTree.nearest(Geometries.point(longitude, latitude), 0.005, 1)
        try {
            val closestNode = closestNodeObserver.first()
            return closestNode.value()
        } catch (e: NoSuchElementException) {
            //If this happens the coordinates are very far from the nearest node, so a appropriate node cannot be found.
            //Therefore an error should be thrown
            throw InvalidParameterException()
        }
    }

    fun solution(end: Long, prev: HashMap<Long, Long>): MutableList<Long> {
        val route = mutableListOf<Long>()
        var current = end
        while (current.toInt() != -1) {
            route.add(current)
            current = prev[current]!!
        }
        return route.asReversed()
    }

    fun getDistanceCost(idOne: Long, idTwo: Long): Double {
        val first = vertices[idOne]
        val second = vertices[idTwo]
        val lonOne = first?.longitude!!
        val latOne = first.latitude
        val lonTwo = second?.longitude!!
        val latTwo = second.latitude
        if (slowNodes.contains(idOne) && slowNodes.contains(idTwo)) {
            return getDistance(lonOne, latOne, lonTwo, latTwo) * 5.0
        }
        if (safeNodes.contains(idOne) && safeNodes.contains(idTwo)) {
            return getDistance(lonOne, latOne, lonTwo, latTwo) * 0.3
        }
        return getDistance(lonOne, latOne, lonTwo, latTwo)
    }

    /**
     * Uses the Haversine formula to work out the distance between two latitudes and longitudes
     * Probably overkill in this case but it works
     */
    fun getDistance(lonOne: Double, latOne: Double, lonTwo: Double, latTwo: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(latTwo - latOne)
        val dLon = Math.toRadians(lonTwo - lonOne)
        val originLat = Math.toRadians(latOne)
        val destinationLat = Math.toRadians(latTwo)

        val a = sin(dLat / 2).pow(2.toDouble()) + sin(dLon / 2).pow(2.toDouble()) * cos(originLat) * cos(destinationLat)
        val c = 2 * asin(sqrt(a))
        return earthRadiusKm * c
    }

    /**
     * Finds the largest strongly connnected component and deletes all other vertices
     * This can be done using kosaraju's algorithm
     */
    fun pruneDisconnected() {
        fun getPostOrderTraversal(vertice : Long, visited : HashSet<Long>, getNeighbours : (Long) -> HashSet<Long>): List<Long> {
            var searchStack = Stack<Long>()
            var postOrder = LinkedList<Long>()
            searchStack.add(vertice)
            while (searchStack.size != 0)
            {
                var current = searchStack.pop()
                if (!visited.contains(current))
                {
                    visited.add(current)
                    postOrder.add(current)
                    for (neighbour in getNeighbours(current))
                    {
                        searchStack.push(neighbour)
                    }
                }
            }
            return postOrder
        }
        var visited = HashSet<Long>()
        val L = LinkedList<Long>()
        for (vertice in vertices)
        {
            for (item in getPostOrderTraversal(vertice.key,visited) { id -> vertices[id]!!.connections })
            {
                L.addFirst(item)
            }
        }
        visited = HashSet<Long>()
        val components = HashMap<Long, HashSet<Long>>()
        for (vertice in L)
        {
            var postOrder = getPostOrderTraversal(vertice,visited) { id -> vertices[id]!!.incomingConnections }
            if (postOrder.size != 0)
            {
                components[vertice] = HashSet<Long>()
                for (item in postOrder)
                {
                    components[vertice]?.add(item)
                }
            }
        }
        val mainComponent = components.maxByOrNull { component -> component.value.size }!!
        val before = vertices.size
        vertices = vertices.filter { item -> mainComponent.value.contains(item.key) } as HashMap<Long, GeographicNode>
        val after = vertices.size
        println("Shrunk vertices from $before to $after")
        for (vertice in vertices.values)
        {
            vertice.connections = vertice.connections.filter { id -> vertices.containsKey(id) }.toHashSet()
            vertice.incomingConnections = vertice.incomingConnections.filter { id -> vertices.containsKey(id)}.toHashSet()
        }
    }

    /**
     * Uses a Heuristic to enact the contraction hierachies algorithm, and contract the graph by order of important nodes.
     * If the graph has already been contracted the algorithm is not run.
     */
    fun contractGraph(distanceCost: Double) {
        if (contractedGraphs.containsKey(distanceCost)) return
        var newContracted = ContractableGraph(distanceCost)
        newContracted.createGraph(this)
        newContracted.contractGraph(this)
        contractedGraphs[distanceCost] = newContracted
    }

    /**
     * Uses the cosine rule to calculate the angle between the previous and the next node about the current node.
     * If this angle is less than 115 degrees and the current node is not listed in SafeNode it returns the given cost
     */
    fun getTurnCost(prev: Long, current: Long, next: Long, cost: Double): Double {
        if (cost == 0.0) return 0.0
        if (prev.toInt() == -1) return 0.0
        val a = getDistanceCost(prev, next)
        val b = getDistanceCost(prev, current)
        val c = getDistanceCost(current, next)
        val angle = Math.toDegrees(acos((b.pow(2) + c.pow(2) - a.pow(2)) / (2 * b * c)))
        if (angle < 115.0) {
            if (safeNodes.contains(current) && !slowNodes.contains(current)) //if node is part of a cycle lane, but not part of a footpath turns are acceptable
            {
                return 0.0
            }
            return cost
        } else {
            return 0.0
        }
    }

    fun findRoute(
        latitudeOne: Double,
        longitudeOne: Double,
        latitudeTwo: Double,
        longitudeTwo: Double,
        accidentsPerKilometre: Double,
        accidentsPerTurn: Double,
        showVisited: Boolean
    ): List<Long> {
        return findRoute(
            getNearestNode(latitudeOne, longitudeOne),
            getNearestNode(latitudeTwo, longitudeTwo),
            accidentsPerKilometre,
            accidentsPerTurn,
            showVisited
        )
    }

    fun findRoute(
        start: Long,
        end: Long,
        accidentsPerKilometre: Double,
        accidentsPerTurn: Double,
        showVisited: Boolean
    ): List<Long> {
        if (contractedGraphs.containsKey(accidentsPerKilometre) && accidentsPerTurn == 0.0) {
            return findContractedRoute(start, end, accidentsPerKilometre, showVisited)
        } else {
            return findRouteNonContracted(start, end, accidentsPerKilometre, accidentsPerTurn)
        }
    }

    /**
     * Bidirectional Dijkstra on the contracted Graph
     */
    private fun findContractedRoute(
        start: Long,
        end: Long,
        accidentsPerKilometre: Double,
        showVisited: Boolean
    ): List<Long> {
        return contractedGraphs[accidentsPerKilometre]!!.findRoute(start, end, this, showVisited)
    }

    /**
     * Basic dijkstra
     */
    private fun calculateRoute(
        start: Long,
        end: Long,
        accidentsPerKilometre: Double,
        accidentsPerTurn: Double
    ): Pair<MutableList<Long>, Double> {
        val F = PriorityQueue<DoubleTuple>()
        val dist = HashMap<Long, Double>()
        val prev = HashMap<Long, Long>()
        dist[start] = 0.0
        prev[start] = -1
        var u: Long
        val toAdd = DoubleTuple(start, 0.0)
        F.add(toAdd)
        while (F.size != 0) {
            val item = F.poll()
            u = item.id
            if (dist[u] != item.dist) continue
            if (u == end) {
                return Pair(solution(end, prev), dist[end]!!)
            }
            for (neighbour in vertices[u]?.connections!!) {
                var alt = dist[u]?.plus(vertices[neighbour]?.weight!!)
                alt = alt?.plus(getDistanceCost(u, neighbour) * accidentsPerKilometre)
                alt = alt?.plus(getTurnCost(prev[u]!!, u, neighbour, accidentsPerTurn))
                if (alt != null) {
                    if (!dist.containsKey(neighbour) || alt < dist[neighbour]!!) {
                        dist[neighbour] = alt
                        prev[neighbour] = u
                        val toAdd = DoubleTuple(neighbour, dist[neighbour]!!)
                        F.add(toAdd)
                    }
                }
            }
        }
        throw InvalidParameterException()
    }

    fun findRouteNonContracted(
        start: Long,
        end: Long,
        accidentsPerKilometre: Double,
        accidentsPerTurn: Double
    ): MutableList<Long> {
        return calculateRoute(start, end, accidentsPerKilometre, accidentsPerTurn).first
    }

    fun routeCost(start: Long, end: Long, accidentsPerKilometre: Double, accidentsPerTurn: Double): Double {
        return calculateRoute(start, end, accidentsPerKilometre, accidentsPerTurn).second
    }
}

