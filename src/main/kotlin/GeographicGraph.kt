import com.github.davidmoten.rtree2.Entries
import com.github.davidmoten.rtree2.Entry
import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Geometry
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.json.JSONArray
import java.io.File
import java.security.InvalidParameterException
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.acos
import kotlin.math.pow


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
    var isContracted = false //Tells methods such as findRoute which version to use
    @Transient
    var nodeTree: RTree<Long, Geometry> = RTree.star().maxChildren(30).create()

    @Serializable
    class GeographicNode(val longitude: Double, val latitude: Double) {
        var weight: Double = 0.0
        var connections = HashSet<Long>()
    }
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

    //This would be the init method but some things are configured externally first
    fun setup()
    {
        if(nodeTree.size()!=0)
        {
           return
        }
        var nodeList = mutableListOf<Entry<Long,Geometry>>()
        for (node in vertices)
        {
            nodeList.add(Entries.entry(node.key,Geometries.point(node.value.longitude,node.value.latitude)))
        }
        nodeTree = RTree.star().maxChildren(28).create(nodeList)
    }
    fun getRandomId() : Long
    {
        return vertices.keys.toList()[Random().nextInt(vertices.size)]
    }
    fun addEdge(first: Long, second: Long,oneWay : Boolean) {
        val geographicFirst = vertices[first]
        val geographicSecond = vertices[second]
        if (geographicFirst != null && geographicSecond != null) {
            vertices[first]?.connections?.add(second)
            if(!oneWay) {
                vertices[second]?.connections?.add(first)
            }
        }
    }

    /**
     * This function reads in the accident data, then adds it as a  weight to all nodes.
     */
    fun gatherWeights() {
        val accidentFile = File("output.json")
        val accidents = JSONArray(accidentFile.inputStream().readBytes().toString(Charsets.UTF_8))
        var counter = 0
        for (accident in accidents) {
            val parsedAccident = accident.toString().split(",")//hacky but will work
            val latitude = parsedAccident[0].substring(1).toDouble()
            val longitude = parsedAccident[1].toDouble()
            val severity = parsedAccident[2].substring(1, parsedAccident[2].length - 2)
            try {
                val accidentWayObserver = nodeTree.nearest(Geometries.point(longitude, latitude), 0.01, 1)
                if (accidentWayObserver.first().value() != null) {
                    val additionNode = vertices[accidentWayObserver.first().value()]
                    if (additionNode != null) {
                        when (severity) {

                            "Slight" -> additionNode.weight += 1
                            "Severe" -> additionNode.weight += 2
                            "Fatal" -> additionNode.weight += 3
                        }
                    }
                }
            }
            catch(e : NoSuchElementException)
            {
                continue
            }
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

    fun getDistance(idOne: Long, idTwo : Long): Double {
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
            return getDistance(lonOne, latOne, lonTwo, latTwo) * 0.4
        }
        return getDistance(lonOne, latOne, lonTwo, latTwo)
    }
    /**
     * Uses the Haversine formula to work out the distance between two latitudes and longitudes
     * Probably overkill in this case but it works
     */
    fun getDistance(lonOne: Double, latOne : Double, lonTwo : Double, latTwo : Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(latTwo - latOne)
        val dLon = Math.toRadians(lonTwo - lonOne)
        val originLat = Math.toRadians(latOne)
        val destinationLat = Math.toRadians(latTwo)

        val a = Math.pow(Math.sin(dLat / 2), 2.toDouble()) + Math.pow(Math.sin(dLon / 2), 2.toDouble()) * Math.cos(originLat) * Math.cos(destinationLat)
        val c = 2 * Math.asin(Math.sqrt(a))
        return earthRadiusKm * c
    }
    fun pruneDisconnected (start : Long)
    {
        val visited = HashSet<Long>()
        val toVisit = PriorityQueue<Long>()
        toVisit.add(start)
        while(toVisit.size!=0)
        {
            val i = toVisit.poll()
            visited.add(i)
           for (x in vertices[i]?.connections!!)
           {
               if (!visited.contains(x))
               {
                   toVisit.add(x)
               }
           }
        }
        val before = vertices.size
        vertices = vertices.filter { item -> visited.contains(item.key) } as HashMap<Long, GeographicNode>
        val after = vertices.size
        println("Shrunk vertices from $before to $after")
    }

    /**
     * Uses a Heuristic to enact the contraction hierachies algorithm, and contract the graph by order of important nodes.
     * If the graph has already been contracted the algorithm is not run.
     */
    fun contractGraph() {
        if(isContracted) return
    }

    /**
     * Uses the cosine rule to calculate the angle between the previous and the next node about the current node.
     * If this angle is less than 115 degrees and the current node is not listed in SafeNode it returns the given cost
     */
    fun getTurnCost(prev: Long, current: Long, next: Long, cost : Double) : Double
    {
        if(prev.toInt()==-1) return 0.0
        val a = getDistance(prev,next)
        val b = getDistance(prev,current)
        val c = getDistance(current,next)
        val angle = Math.toDegrees(acos((b.pow(2)+c.pow(2)-a.pow(2))/(2*b*c)))
        if (angle < 115.0)
        {
            if (safeNodes.contains(current) && !slowNodes.contains(current)) //if node is part of a cycle lane, but not part of a footpath turns are acceptable
            {
                return 0.0
            }
            return cost
        }
        else
        {
            return 0.0
        }
    }

    fun findRoute(start: Long, end: Long, accidentsPerKilometre: Double, accidentsPerTurn : Double): MutableList<Long> {
        if (isContracted)
        {
            return findContractedRoute(start,end,accidentsPerKilometre,accidentsPerTurn)
        }
        else
        {
            return findRouteNonContracted(start,end,accidentsPerKilometre,accidentsPerTurn)
        }
    }
    /**
     * Bidirectional Dijkstra on the contracted Graph
     */
    private fun findContractedRoute(start: Long, end: Long, accidentsPerKilometre: Double, accidentsPerTurn: Double): MutableList<Long> {
       return findRouteNonContracted(start,end,accidentsPerKilometre,accidentsPerTurn)
    }
    /**
     * Basic dijkstra
     */
    private fun findRouteNonContracted(start : Long, end : Long, accidentsPerKilometre: Double,accidentsPerTurn: Double) : MutableList<Long>
    {
        val F = PriorityQueue<Tuple>()//Heuristic score priority Queue
        val FLookUp = HashMap<Long,Tuple>()
        val dist = HashMap<Long, Double>()
        val prev = HashMap<Long, Long>()
        dist[start] = 0.0
        var u: Long
        for (i in vertices.keys) {
            if(i!=start) {
                dist[i] = Double.MAX_VALUE
                val toAdd = Tuple(i,dist[i]!!)
                F.add(toAdd)
            }
            prev[i] = -1
        }
        val toAdd = Tuple(start,getDistance(start,end)*accidentsPerKilometre)
        F.add(toAdd)
        while (F.size != 0) {
            u = F.poll().id
            if (u == end) {
                return solution(end, prev)
            }
            for (neighbour in vertices[u]?.connections!!) {
                var alt = dist[u]?.plus(vertices[neighbour]?.weight!!)
                alt = alt?.plus(getDistance(u,neighbour)*accidentsPerKilometre)
                alt = alt?.plus(getTurnCost(prev[u]!!,u,neighbour,accidentsPerTurn))
                if (alt != null) {
                    if (alt < dist[neighbour]!!) {
                        dist[neighbour] = alt
                        prev[neighbour] = u
                        val toAdd = Tuple(neighbour,dist[neighbour]!!)
                        F.add(toAdd)
                    }
                }
            }
        }
        throw InvalidParameterException()
    }
}

