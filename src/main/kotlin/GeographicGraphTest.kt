import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance
import java.util.*
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class GeographicGraphTest {
    var testMap = OpenStreetMap("maps/ways.osm")
    var testGraph = testMap.cyclableGraph
    @org.junit.jupiter.api.Test
    fun addEdge() {
        var testNodeOne :Long = 1688516455
        var testNodeTwo :Long = 1128011467
        testGraph.addEdge(testNodeOne,testNodeTwo,false)
        assert(testGraph.vertices[testNodeOne]!!.connections.contains(testNodeTwo) && testGraph.vertices[testNodeTwo]!!.connections.contains(testNodeOne))
        testNodeOne = 8088913233
        testNodeTwo = 243458793
        testGraph.addEdge(testNodeOne,testNodeTwo,true)
        assert(testGraph.vertices[testNodeOne]!!.connections.contains(testNodeTwo) && !testGraph.vertices[testNodeTwo]!!.connections.contains(testNodeOne))
    }

    @org.junit.jupiter.api.Test
    fun gatherWeights() {
        for(vertice in testGraph.vertices.keys)
        {
            testGraph.vertices[vertice]!!.weight = 0.0
        }
        testGraph.gatherWeights()
        var total = 0.0
        for(vertice in testGraph.vertices.keys)
        {
            total += testGraph.vertices[vertice]!!.weight
        }
        assertEquals(74486.0,total)

    }

    @org.junit.jupiter.api.Test
    fun getDistance() {
        assertEquals(testGraph.getDistance(173711674,5222626957),26.34972872449454)
        assertEquals(testGraph.getDistance(0.01,51.0,0.0,51.0),0.6997723466506964)
    }

    @org.junit.jupiter.api.Test
    fun pruneDisconnected() {
        val size = testGraph.vertices.size
        testGraph.pruneDisconnected(5222626957)
        assertEquals(size,testGraph.vertices.size)//everything should already have been pruned that can be pruned
    }

    @org.junit.jupiter.api.Test
    fun getTurnCost() {
        assertEquals(testGraph.getTurnCost(21596680,21596679,5220892088,10.0),10.0)
    }

    @org.junit.jupiter.api.Test
    fun findRoute() {
        var route = testGraph.findRoute(68317710,1939558649,10.0,10.0)
        var distance = 0.0
        for (i in 1..(route.size-1))
            distance += testGraph.getDistance(route[i-1],route[i])
        assertEquals(17.81661650553658,distance)
    }
}