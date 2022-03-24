import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class ContractableGraphTest {
    var testGraph = OpenStreetMap("maps/testarea.osm").cyclableGraph

    @Test
    fun createGraph() {
        val contractedGraph = ContractableGraph(15.0)
        contractedGraph.createGraph(testGraph)
    }

    @Test
    fun findRoute() {
        val contractedGraph = ContractableGraph(15.0)
        contractedGraph.createGraph(testGraph)
        contractedGraph.contractGraph(testGraph)
        val route = contractedGraph.findRoute(8861811044, 8864562252,testGraph,false)
        var distance = 0.0
        for (i in 1 until route.size)
            distance += testGraph.getDistanceCost(route[i - 1], route[i])
        assertEquals(0.14516271195954372,distance)
    }
}