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
import java.awt.SystemTray
import java.io.File
import java.nio.file.Path
import java.util.Collections.max
import java.util.Random


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
        println("Gathering weights")
        cyclableGraph.gatherWeights(nodeTree)
        for (node in safeNodes)
        {
            cyclableGraph.vertices[node]?.weight = 0.0
        }
        var startNode = cyclableGraph.vertices.keys.toList()[Random().nextInt(cyclableGraph.vertices.size)]
        var endNode = cyclableGraph.vertices.keys.toList()[Random().nextInt(cyclableGraph.vertices.size)]
        println("Started Route Finding")
        var startTime = System.nanoTime()
        var gpx: GPX = GPX.builder().build()
        var counter = 0
        while (counter < 0)
        {
            startNode = cyclableGraph.vertices.keys.toList()[Random().nextInt(cyclableGraph.vertices.size)]
            endNode = cyclableGraph.vertices.keys.toList()[Random().nextInt(cyclableGraph.vertices.size)]
            counter += 1
            gpx = writeNewTrack(startNode,endNode,10.0,10.0,gpx)!!
        }
        startNode = 725292357
        endNode = 20821091
        gpx = writeNewTrack(startNode,endNode,10.0,10.0,gpx)!!
        GPX.write(gpx,Path.of("route.gpx"))
        var endTime = (System.nanoTime() - startTime)/1000000000.0
        println("Time taken for 10 routes was was $endTime")
    }
    fun writeNewTrack(start: Long, end: Long, dist : Double, turn : Double, gpx : GPX): GPX {
        var route = cyclableGraph.findRoute(start,end,dist,turn)
        var track: TrackSegment = TrackSegment.builder().build()
        for (point in route) {
            var node = cyclableGraph.vertices[point]
            track = track.toBuilder().addPoint { p -> p.lon(node!!.longitude).lat(node!!.latitude) }.build()
        }
        var segment: Track = Track.builder().addSegment(track).build()
        segment.toBuilder().name("$dist,$turn")
        return gpx.toBuilder().addTrack(segment).build()
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
        //TODO add footpaths where dismounts are allowed but at an extra cost
        var oneWay = false
        var cycleWay = false
        var nodes = mutableListOf<Long>()
        val acceptedRoads =      mutableListOf<String>("trunk","service","primary","secondary","tertiary","unclassified","residential","primary_link","secondary_link","tertiary_link","living_street","cycleway")
        var disallowedSurfaces = mutableListOf<String>("gravel","dirt","grass","pebblestone")
        var disallowedAccess =   mutableListOf<String>("no")
        var highSpeed =          mutableListOf<String>("40 mph", "50 mph", "60 mph", "70 mph")
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
                if (subElement.attributeValue("k") == "bicycle" && disallowedAccess.contains(subElement.attributeValue("v"))) return
                if (subElement.attribute("k").value == "oneway" && subElement.attribute("v").value == "yes") oneWay = true
                if (subElement.attribute("k").value == "highway" && subElement.attribute("v").value == "cycleway") cycleWay = true
                if (subElement.attributeValue("k") == "maxspeed" && highSpeed.contains(subElement.attributeValue("v"))) return
            }
        }
        if (cycleWay) {
            safeNodes.addAll(nodes)
            cyclableGraph.safeNodes.addAll(nodes)
        }
        for (i in 1..(nodes.size-1)) {
            cyclableGraph.addEdge(nodes[i-1],nodes[i],oneWay)
        }
        }
}