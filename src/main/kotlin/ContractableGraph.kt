import kotlinx.serialization.Serializable
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

//TODO allow routing with a passed in inputGraph
@Serializable
class ContractableGraph(private var distanceCost: Double, var turnCost: Double) {
    /**
     * In the initalisation phase we create a new graph from the previous graph
     */
    private var vertices = HashMap<Long, ContractableNode>()
    private var noShortcuts = 0

    /**
     * A vertice that represents two connected edges and their associated connections (given as id's)
     */
    @Serializable
    class ContractableNode(val id: Long) {
        var deleted = false
        var hierachy = 0
        var connections = HashMap<Long, Double>()
        var incomingConnections = HashMap<Long, Double>()
        var shortcutConnections =
            HashMap<Long, Double>() //gives the weights of a shortcut based connection to another node
        var incomingShortcuts = HashMap<Long, Double>()
        var shortcutRoutes = HashMap<Long, Long>() //gives the node through which the shortcut travels
        var allOutgoingConnections = listOf<Long>()
        var allIncomingConnections = listOf<Long>()
    }


    /**
     * Finds all edges
     * Checks every edge to see if it is reversible, if so it is added twice
     */
    //TODO edges are not symetrical i.e node1 to node2 is different from node2 to node1 so use the edgelookup system to fix this
    fun createGraph(inputGraph: GeographicGraph) {
        println("Creating Graph")
        for (vertice in inputGraph.vertices) {
            var newNode = vertices[vertice.key]
            if (newNode == null) newNode = ContractableNode(vertice.key)
            for (connectedNode in vertice.value.connections) {
                newNode.connections[connectedNode] = inputGraph.routeCost(vertice.key, connectedNode, distanceCost, 0.0)
                if (vertices.containsKey(connectedNode)) vertices[connectedNode]!!.incomingConnections[vertice.key] =
                    inputGraph.routeCost(vertice.key, connectedNode, distanceCost, 0.0)
                else {
                    val newConnectedNode = ContractableNode(connectedNode)
                    newConnectedNode.incomingConnections[vertice.key] =
                        inputGraph.routeCost(vertice.key, connectedNode, distanceCost, 0.0)
                    vertices[connectedNode] = newConnectedNode
                }
            }
            vertices[vertice.key] = newNode
        }
    }

    /**
     * Finds the route using contraction hierachies.
     * First the set of nodes with a higher hierarchy from the from node are calculated
     * Then the set of nodes with a higher hierachy from the to node (backwards) are calculated
     * Then the intersection of the set of settled nodes is found and the minimum found.
     * Then the route is created by recursively looking up the shortcuts
     */
    fun findRoute(from: Long, to: Long, inputGraph: GeographicGraph, showVisited: Boolean): List<Long> {
        //first we generate the upwards and downwards search spaces using simple queues
        val Q: Queue<Long> = LinkedList<Long>()
        val upwardsSpace = HashSet<Long>()
        val downSpace = HashSet<Long>()
        Q.add(from)
        while (Q.size != 0) {
            val current = Q.poll()
            upwardsSpace.add(current)
            for (neighbour in vertices[current]!!.allOutgoingConnections) {
                if (vertices[neighbour]!!.hierachy > vertices[current]!!.hierachy && !upwardsSpace.contains(neighbour)) Q.add(
                    neighbour
                )
            }
        }
        Q.add(to)
        while (Q.size != 0) {
            val current = Q.poll()
            downSpace.add(current)
            for (neighbour in vertices[current]!!.allOutgoingConnections) {
                if (vertices[neighbour]!!.hierachy > vertices[current]!!.hierachy && !downSpace.contains(neighbour)) Q.add(
                    neighbour
                )
            }
        }
        var F = PriorityQueue<DoubleTuple>()
        val settledFrom = HashSet<Long>()
        val dist = HashMap<Long, Double>()
        val prev = HashMap<Long, Long>()
        var u: Long
        var toAdd = DoubleTuple(from, 0.0)
        dist[from] = 0.0
        F.add(toAdd)
        while (F.size != 0) {
            u = F.poll().id
            settledFrom.add(u)
            for (neighbour in vertices[u]!!.allOutgoingConnections) {
                if (!upwardsSpace.contains(neighbour)) continue
                var cost: Double =
                    if (vertices[u]!!.connections.containsKey(neighbour)) vertices[u]!!.connections[neighbour]!!
                    else vertices[u]!!.shortcutConnections[neighbour]!!
                val alt = cost + dist[u]!!
                if (!dist.containsKey(neighbour) || alt < dist[neighbour]!!) {
                    dist[neighbour] = alt
                    prev[neighbour] = u
                    toAdd = DoubleTuple(neighbour, dist[neighbour]!!)
                    F.add(toAdd)
                }
            }
        }
        F = PriorityQueue<DoubleTuple>()
        val settledTo = HashSet<Long>()
        val distTo = HashMap<Long, Double>()
        val prevTo = HashMap<Long, Long>()
        distTo[to] = 0.0
        toAdd = DoubleTuple(to, 0.0)
        F.add(toAdd)
        while (F.size != 0) {
            u = F.poll().id
            settledTo.add(u)
            for (neighbour in vertices[u]!!.allIncomingConnections) {
                if (!downSpace.contains(neighbour)) continue
                var cost: Double =
                    if (vertices[u]!!.incomingConnections.containsKey(neighbour)) vertices[u]!!.incomingConnections[neighbour]!!
                    else vertices[u]!!.incomingShortcuts[neighbour]!!
                val alt = cost + distTo[u]!!
                if (!distTo.containsKey(neighbour) || alt < distTo[neighbour]!!) {
                    distTo[neighbour] = alt
                    prevTo[neighbour] = u
                    toAdd = DoubleTuple(neighbour, distTo[neighbour]!!)
                    F.add(toAdd)
                }
            }
        }
        var minimumNode: Long = -1
        var minimumCost = Double.MAX_VALUE
        for (i in settledTo.intersect(settledFrom)) {
            if (dist[i]!! + distTo[i]!! - inputGraph.vertices[i]!!.weight < minimumCost) {
                minimumNode = i
                minimumCost = dist[i]!! + distTo[i]!! - inputGraph.vertices[i]!!.weight
            }
        }
        println(minimumCost)
        return if (showVisited) settledTo.intersect(settledFrom).toList()
        else deContractRoute(minimumNode, prev, prevTo)
    }

    /**
     * With given conditions, decontracts the route
     */
    private fun deContractRoute(minimumNode: Long, prev: HashMap<Long, Long>, prevTo: HashMap<Long, Long>): List<Long> {
        val route = mutableListOf<Long>(minimumNode)
        while (prev.containsKey(route[0]) && prev[route[0]]!! != -1L) {
            route.add(0, prev[route[0]]!!)
        }
        while (prevTo.containsKey(route.last()) && prevTo[route.last()]!! != -1L) {
            route.add(prevTo[route.last()]!!)
        }
        return unpackRoute(route)
    }

    private fun unpackRoute(route: MutableList<Long>): MutableList<Long> {
        while (true) {
            var finished = true
            for (i in 1 until route.size) {
                if (route[i - 1] == -1L) continue
                val current = vertices[route[i - 1]]!!
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

    /**
     * Creates another graph: G*, which is contracted
     * Uses a edge change heuristic, which aims to minimise the number of edges reduced
     */
    fun contractGraph(inputGraph: GeographicGraph) {
        println("Contracting Graph of size ${vertices.size}")
        var current = 1
        var contractionQueue = PriorityQueue<IntTuple>()
        var count = 0
        for (vertice in vertices.keys) {
            contractionQueue.add(IntTuple(vertice, getHeuristicValue(vertice, inputGraph)))
            println(contractionQueue.size)
        }
        while (contractionQueue.size != 0) {
            if (count == 100) {
                println("Recalculating Queue")
                val newQueue = PriorityQueue<IntTuple>()
                while (contractionQueue.size != 0) {
                    val current = contractionQueue.poll()
                    val edgeDifference = getHeuristicValue(current.id, inputGraph)
                    newQueue.add(IntTuple(current.id, edgeDifference))
                }
                contractionQueue = newQueue
                count = 0
            }
            val next = contractionQueue.poll()
            val oldEdges = next.dist
            val newEdges = getHeuristicValue(next.id, inputGraph)

            if (oldEdges != newEdges) {
                count += 1
                contractionQueue.add(IntTuple(next.id, newEdges))
                continue
            } else {
                count = 0
            }
            contractNode(next.id, current, inputGraph)
            current += 1
            println(newEdges)
            println(contractionQueue.size)
        }
        println("Contraction finished")
        for (vertice in vertices) {
            vertice.value.allOutgoingConnections =
                vertice.value.shortcutConnections.keys.union(vertice.value.connections.keys).toList()
            vertice.value.allIncomingConnections =
                vertice.value.incomingShortcuts.keys.union(vertice.value.incomingConnections.keys).toList()
        }
    }

    /**
     * Performs the act of contraction on the input node
     */
    private fun contractNode(node: Long, current: Int, inputGraph: GeographicGraph) {
        val nodeObj = vertices[node]!!
        for (from in nodeObj.incomingConnections.keys.union(nodeObj.incomingShortcuts.keys)
            .filter { x -> !vertices[x]!!.deleted }) {
            for (to in getShortest(from, node, inputGraph, false)) {
                if (from != to) {
                    val fromVertice = vertices[from]!!
                    val nodeObj = vertices[node]!!
                    val toVertice = vertices[to]!!
                    noShortcuts += 1
                    var connectionWeight = 0.0
                    connectionWeight += if (fromVertice.connections.containsKey(node)) fromVertice.connections[node]!! else fromVertice.shortcutConnections[node]!!
                    connectionWeight += if (nodeObj.connections.containsKey(to)) nodeObj.connections[to]!! else nodeObj.shortcutConnections[to]!!
                    fromVertice.shortcutConnections[to] = connectionWeight
                    fromVertice.shortcutRoutes[to] = node
                    toVertice.incomingShortcuts[from] = connectionWeight
                }
            }
        }
        nodeObj.deleted = true
        nodeObj.hierachy = current
    }

    /**
     * Generates a tree which can then be checked for shortest routes
     * The tree is built until all neighbours of the about node are found, then queried to find if the shortest route goes through the about node
     * The estimation feature enables faster estimations of edge differences by terminating the search after a given number of nodes,
     * but can be disabled for the real contraction operation
     */
    private fun getShortest(from: Long, about: Long, inputGraph: GeographicGraph, estimation: Boolean): MutableList<Long> {
        val toFind = vertices[about]!!.connections.keys.union(vertices[about]!!.shortcutConnections.keys)
            .filter { x -> !vertices[x]!!.deleted }.toHashSet()
        HashSet<Long>()
        val F = PriorityQueue<DoubleTuple>()
        val dist = HashMap<Long, Double>()
        val prev = HashMap<Long, Long>()
        var numsettled = 0
        dist[from] = 0.0
        prev[from] = -1
        var u: Long
        var toAdd = DoubleTuple(from, 0.0)
        F.add(toAdd)
        while (F.size != 0) {
            u = F.poll().id
            toFind.remove(u)
            numsettled += 1
            for (neighbour in vertices[u]!!.connections.keys) {
                var alt = dist[u]!! + vertices[u]!!.connections[neighbour]!!
                if (!dist.containsKey(neighbour) || alt < dist[neighbour]!!) {
                    toFind.remove(neighbour)
                    dist[neighbour] = alt
                    prev[neighbour] = u
                    toAdd = DoubleTuple(neighbour, dist[neighbour]!!)
                    F.add(toAdd)
                }
            }
            if (toFind.size == 0 || (numsettled == 10000 && estimation)) break
        }
        val shortestThrough = mutableListOf<Long>()
        for (i in vertices[about]!!.connections.keys.union(vertices[about]!!.shortcutConnections.keys)
            .filter { x -> !vertices[x]!!.deleted }) {
            if (i == from) continue
            if (!prev.containsKey(i)) shortestThrough.add(i) //i has not been reached because contraction has been terminated early, therefore a shortcut is necessary
            else {
                val route = inputGraph.solution(i, prev)
                if (route.contains(about)) shortestThrough.add(i) //The shortest route goes through the node therefore a shortcut is necessary
            }
        }
        return shortestThrough
    }

    /**
     * Works out the edge difference and the number of deleted neighbours and uses these to calculate a heuristic
     * The edge difference is  equivalent to the number of shortcuts that would be added if the node were to be deleted
     * or the number of the routes between adjacent nodes that pass through this node
     * minus the number of routes that originally go through this node.
     */
    private fun getHeuristicValue(node: Long, inputGraph: GeographicGraph): Int {
        val nodeObj = vertices[node]!!
        var count = 0
        for (from in nodeObj.incomingConnections.keys.union(nodeObj.incomingShortcuts.keys)
            .filter { x -> !vertices[x]!!.deleted }) {
            count += getShortest(from, node, inputGraph, true).size
        }
        count -= (nodeObj.incomingConnections.keys.union(
            nodeObj.incomingShortcuts.keys.union(
                nodeObj.connections.keys.union(
                    nodeObj.shortcutConnections.keys
                )
            )
        )).filter { x -> !vertices[x]!!.deleted }.size
        count += (nodeObj.incomingConnections.keys.union(
            nodeObj.incomingShortcuts.keys.union(
                nodeObj.connections.keys.union(
                    nodeObj.shortcutConnections.keys
                )
            )
        )).filter { x -> vertices[x]!!.deleted }.size * 5//maintains uniformity
        return count
    }

    /**
     * Just a standard dijkstra implementation
     */
    private fun isShortest(from: Long, by: Long, to: Long, inputGraph: GeographicGraph): Boolean {
        val route = inputGraph.findRouteNonContracted(from, to, distanceCost, 0.0)
        return route.contains(by)
    }
}