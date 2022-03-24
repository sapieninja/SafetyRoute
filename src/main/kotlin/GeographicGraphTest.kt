import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.math.abs

internal class GeographicGraphTest {
    var testMap = OpenStreetMap("maps/testarea.osm")
    var testGraph = testMap.cyclableGraph

    @Test
    fun addEdge() {
        var testNodeOne: Long = 1767962872
        var testNodeTwo: Long = 8883453446
        testGraph.addEdge(testNodeOne, testNodeTwo, false)
        assert(
            testGraph.vertices[testNodeOne]!!.connections.contains(testNodeTwo) && testGraph.vertices[testNodeTwo]!!.connections.contains(
                testNodeOne
            )
        )
        testNodeOne = 1767962860
        testNodeTwo = 8889691941
        testGraph.addEdge(testNodeOne, testNodeTwo, true)
        assert(
            testGraph.vertices[testNodeOne]!!.connections.contains(testNodeTwo) && !testGraph.vertices[testNodeTwo]!!.connections.contains(
                testNodeOne
            )
        )
    }

    @Test
    fun gatherWeights() {
        for (vertice in testGraph.vertices.keys) {
            testGraph.vertices[vertice]!!.weight = 0.0
        }
        testGraph.gatherWeights()
        var total = 0.0
        for (vertice in testGraph.vertices.keys) {
            total += testGraph.vertices[vertice]!!.weight
        }
        assertEquals(3570.0, total)

    }

    @Test
    fun getRandomId(){
        assert(testGraph.getRandomId() in testGraph.vertices)
    }

    @Test
    fun contractGraph(){
        testGraph.contractGraph(10.0)
    }

    @Test
    fun getDistance() {
        assert(abs(testGraph.getDistance(0.0, 0.0, 1.0, 0.0)-111)<1)
    }
    @Test
    fun getDistanceCost() {
        assertEquals(0.1868205192755297, testGraph.getDistanceCost(1767962851,1767962820))
    }


    @Test
    fun pruneDisconnected() {
        val size = testGraph.vertices.size
        testGraph.pruneDisconnected()
        assertEquals(size, testGraph.vertices.size)//everything should already have been pruned that can be pruned
    }

    @Test
    fun getTurnCost() {
        assertEquals(testGraph.getTurnCost(1767962851, 1767962853, 1767962820, 10.0), 10.0)
        assertEquals(testGraph.getTurnCost(8883476987, 1767962831, 1767962838, 10.0), 0.0)
    }

    @Test
    fun findRoute() {
        var route = testGraph.findRoute(8861811044, 8864562252, 10.0, 10.0, false)
        var distance = 0.0
        for (i in 1 until route.size)
            distance += testGraph.getDistanceCost(route[i - 1], route[i])
        assertEquals(0.14516271195954372, distance)
    }
}