import java.util.*

class ContractableGraph(input : GeographicGraph, distanceCost : Double, turnCost: Double){
    /**
     * In the initalisation phase we create a new edge to edge graph based on path costs from the previous graph
     */
    var inputGraph = input
    var turnCost = turnCost
    var distanceCost = distanceCost
    class EdgeVertice
    {

    }
    init
    {

    }

    /**
     * Finds all edges
     * Checks every edge to see if it is reversible, if so it is added twice
     */
    fun createGraph(start : Long)
    {
       var edgeNo = 1;
        for (middle in inputGraph.vertices) {
            for(start in middle.value.connections)
            {
                for (end in middle.value.connections)
                {
                    if(start!=end)
                    {
                        var cost = getEdgeCost(start,middle.key,end)
                    }
                }
            }
        }
    }
    /**
     * Gets the cost of moving between two edges for our new edge graph.
     */
    fun getEdgeCost(start: Long, middle : Long, end : Long): Double {
        var distance = (inputGraph.getDistance(start,middle) + inputGraph.getDistance(middle,end))/2
        var danger = inputGraph.vertices[middle]!!.weight
        var turning = inputGraph.getTurnCost(start,middle,end,turnCost)
        return distance * distanceCost + turning + danger
    }
}