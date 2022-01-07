import com.github.davidmoten.rtree2.Entry
import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Geometry
import io.jenetics.jpx.GPX
import io.jenetics.jpx.Track
import io.jenetics.jpx.TrackSegment
import io.jenetics.jpx.WayPoint
import kotlinx.serialization.ExperimentalSerializationApi
import org.dom4j.Element
import org.dom4j.io.SAXReader
import java.io.File
import java.nio.file.Path
import java.util.Collections.max


@OptIn(ExperimentalSerializationApi::class)
class OpenStreetMap constructor(filename: String) {
    var cyclableGraph = GeographicGraph()

    /**
     * We put the RTree here because we don't want to have to bother with serializing them
     */

    var nodeTree: RTree<Long, Geometry> = RTree.star().maxChildren(30).create()
    var safeNodes : HashSet<Long> = HashSet<Long>()

    init {
        println("Parsing XML")
        parseXML(filename)
        println("Started pruning operation")
        cyclableGraph.pruneDisconnected(20915039)
        println("Creating rTree")
        for (node in cyclableGraph.vertices)
        {
           nodeTree = nodeTree.add(node.key,Geometries.point(node.value.longitude,node.value.latitude))
        }
        println(nodeTree.size())
        println("Gathering weights")
        cyclableGraph.gatherWeights(nodeTree)
        println(safeNodes.size)
        var maximum = 0.0
        for (id in cyclableGraph.vertices)
        {
           if (id.value.weight > maximum) {
               maximum = id.value.weight
           }
        }
        println(maximum)
        for (node in safeNodes)
        {
            cyclableGraph.vertices[node]?.weight = 0.0
        }
        var longOne = -0.23701801540974204
        var latOne =   51.50571031367023
        var longTwo = 0.007520794635921069
        var latTwo = 51.50085289432013
        var startNode = nodeTree.nearest(Geometries.point(longOne,latOne),0.1,1).first().value()
        var endNode = nodeTree.nearest(Geometries.point(longTwo,latTwo),0.1,1).first().value()
        println("Started Route Finding")
        var startTime = System.nanoTime()
        var route = cyclableGraph.findRoute(startNode,endNode,30.0)
        var gpx: GPX = GPX.builder().build()
        var track: TrackSegment = TrackSegment.builder().build()
        for (point in route) {
            var node = cyclableGraph.vertices[point]
            track = track.toBuilder().addPoint { p -> p.lon(node!!.longitude).lat(node!!.latitude) }.build()
        }
        val segment: Track = Track.builder().addSegment(track).build()
        gpx = gpx.toBuilder().addTrack(segment).build()
        GPX.write(gpx, Path.of("route.gpx"))
        var timeTaken = (System.nanoTime() - startTime)/1000000000.0
        print("Finished finding route in $timeTaken seconds")
    }
    fun parseXML(filename : String)
    {
        var stream = File(filename).inputStream()
        val saxReader = SAXReader()
        var cyclableDocument = saxReader.read(stream)
        val root: Element = cyclableDocument.rootElement
        val it: Iterator<Element> = root.elementIterator()
        while (it.hasNext()) {
            val element: Element = it.next()
            when(element.qName.name)
            {
                "node" -> processNode(element)
                "way"  -> processWay(element)
            }
        }
    }
    fun processNode(node : Element)
    {
       var longitude = node.attribute("lon").value.toDouble()
        var latitude  = node.attribute("lat").value.toDouble()
        cyclableGraph.vertices[node.attribute("id").value.toLong()] = GeographicGraph.GeographicNode(longitude,latitude)
    }
    fun processWay(way : Element)
    {
        var oneWay = false
        var cycleWay = false
        var nodes = mutableListOf<Long>()
        val acceptedRoads = mutableListOf<String>("trunk","primary","secondary","tertiary","unclassified","residential","primary_link","secondary_link","tertiary_link","living_street","cycleway")
        var disallowedSurfaces = mutableListOf<String>("gravel","dirt","grass","pebblestone")
        var disallowedAccess =   mutableListOf<String>("no","private","customers")
        var disallowedBicycles = mutableListOf<String>("no","private","customers","dismount")
        val it = way.elementIterator()
        while(it.hasNext())
        {
            val subElement = it.next()
            if (subElement.qName.name == "nd") {
                nodes.add(subElement.attribute("ref").value.toLong())
            }
            if (subElement.qName.name == "tag")
            {
                if (subElement.attribute("k").value == "highway" && !acceptedRoads.contains(subElement.attribute("v").value)) return
                if (subElement.attribute("k").value == "surface" && disallowedSurfaces.contains(subElement.attribute("v").value)) return
                if (subElement.attribute("k").value == "note" && subElement.attribute("v").value == "towpath") return
                if (subElement.attribute("k").value == "towpath" && subElement.attribute("v").value == "yes") return
                if (subElement.attribute("k").value == "access" && disallowedAccess.contains(subElement.attribute("v").value)) return
                if (subElement.attributeValue("k") == "bicycle" && disallowedBicycles.contains(subElement.attributeValue("v"))) return
                if (subElement.attribute("k").value == "oneway" && subElement.attribute("v").value == "yes") oneWay = true
                if (subElement.attribute("k").value == "highway" && subElement.attribute("v").value == "cycleway") cycleWay = true
            }
        }
        if (cycleWay) safeNodes.addAll(nodes)
        for (i in 1..(nodes.size-1)) {
            cyclableGraph.addEdge(nodes[i-1],nodes[i],oneWay)
        }
        }
}