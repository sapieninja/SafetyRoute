import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Geometry
import kotlinx.serialization.Serializable
import org.json.JSONArray
import java.io.File
import java.security.InvalidParameterException
import java.util.*
import kotlin.collections.HashMap


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

    @Serializable
    class GeographicNode(val longitude: Double, val latitude: Double) {
        var weight: Double = 0.0
        var connections = HashSet<Long>()
    }

    @Serializable
    class GeographicWay(val first: GeographicNode, val second: GeographicNode)

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
     * This function reads in the accident data, then adds it as a weight to all nodes.
     */
    fun gatherWeights(nodeTree: RTree<Long, Geometry>) {
        var accidentFile: File = File("output.json")
        var accidents = JSONArray(accidentFile.inputStream().readBytes().toString(Charsets.UTF_8))
        var counter = 0
        for (accident in accidents) {
            var parsedAccident = accident.toString().split(",")//hacky but will work
            var latitude = parsedAccident[0].substring(1).toDouble()
            var longitude = parsedAccident[1].toDouble()
            var severity = parsedAccident[2].substring(1, parsedAccident[2].length - 2)
            var accidentWayObserver = nodeTree.nearest(Geometries.point(longitude, latitude), 0.01, 1)
            if (accidentWayObserver.first().value() != null) {
                var additionNode = vertices[accidentWayObserver.first().value()]
                if (additionNode != null) {
                    when (severity) {
                        "Slight" -> additionNode.weight += 1
                        "Severe" -> additionNode.weight += 2
                        "Fatal" -> additionNode.weight +=  3
                    }
                }
            }
        }
    }

    fun solution(start: Long, end: Long, prev: HashMap<Long, Long>): MutableList<Long> {
        var route = mutableListOf<Long>()
        var current = end
        while (current != start) {
            route.add(current)
            current = prev[current]!!
        }
        return route.asReversed()
    }

    fun getDistance(idOne: Long, idTwo : Long): Double {
        var first = vertices[idOne]
        var second = vertices[idTwo]
        var lonOne = first?.longitude!!
        var latOne = first?.latitude!!
        var lonTwo = second?.longitude!!
        var latTwo = second?.latitude!!
        return getDistance(lonOne,latOne,lonTwo,latTwo)
    }
    /**
     * Uses the Haversine formula to work out the distance between two latitudes and longitudes
     * Probably overkill in this case but it works
     */
    fun getDistance(lonOne: Double, latOne : Double, lonTwo : Double, latTwo : Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(latTwo - latOne);
        val dLon = Math.toRadians(lonTwo - lonOne);
        val originLat = Math.toRadians(latOne);
        val destinationLat = Math.toRadians(latTwo);

        val a = Math.pow(Math.sin(dLat / 2), 2.toDouble()) + Math.pow(Math.sin(dLon / 2), 2.toDouble()) * Math.cos(originLat) * Math.cos(destinationLat);
        val c = 2 * Math.asin(Math.sqrt(a));
        return earthRadiusKm * c;
    }
    fun pruneDisconnected (start : Long)
    {
        var visited = HashSet<Long>()
        var toVisit = PriorityQueue<Long>()
        toVisit.add(start)
        while(toVisit.size!=0)
        {
            var i = toVisit.poll()
            visited.add(i)
           for (i in vertices[i]?.connections!!)
           {
               if (!visited.contains(i))
               {
                   toVisit.add(i)
               }
           }
        }
        var before = vertices.size
        vertices = vertices.filter { item -> visited.contains(item.key) } as HashMap<Long, GeographicNode>
        var after = vertices.size
        println("Shrunk vertices from $before to $after")
    }

    /**
     * Basic djikstra in an infinite graph implementation
     */
    fun findRoute(start: Long, end: Long, accidentsPerKilometre: Double): MutableList<Long> {
        class DistTuple(val id : Long, val dist : Double) : Comparable<DistTuple>
        {
            override fun compareTo(other: DistTuple): Int {
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
        var Q = PriorityQueue<DistTuple>()//Actual score priority Queue
        var F = PriorityQueue<DistTuple>()//Heuristic score priority Queue
        var dist = HashMap<Long, Double>()
        var prev = HashMap<Long, Long>()
        dist[start] = 0.0
        var u: Long = 0
        for (i in vertices.keys) {
            if(i!=start) {
                dist[i] = Double.MAX_VALUE
                F.add(DistTuple(i,dist[i]!!))
            }
            prev[i] = -1
            Q.add(DistTuple(i,dist[i]!!))
        }
        F.add(DistTuple(start,getDistance(start,end)*accidentsPerKilometre))
        while (F.size != 0) {
            u = F.poll().id
            if (u == end) {
                return solution(start, end, prev)
            }
            for (neighbour in vertices[u]?.connections!!) {
                var alt = dist[u]?.plus(vertices[neighbour]?.weight!!)
                alt = alt?.plus(getDistance(u,neighbour)*accidentsPerKilometre)
                if (alt != null) {
                    if (alt < dist[neighbour]!!) {
                        dist[neighbour] = alt
                        prev[neighbour] = u
                        Q.add(DistTuple(neighbour,dist[neighbour]!!))
                        F.add(DistTuple(neighbour,dist[neighbour]!!+getDistance(neighbour,end)*accidentsPerKilometre))
                    }
                }
            }
        }
        throw InvalidParameterException()
    }
}