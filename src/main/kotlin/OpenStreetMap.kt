import com.github.davidmoten.rtree2.Entry
import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Geometry
import kotlinx.serialization.ExperimentalSerializationApi
import org.dom4j.Element
import org.dom4j.io.SAXReader
import java.io.File
import java.nio.file.Path
import io.jenetics.jpx.GPX
import io.jenetics.jpx.Track
import io.jenetics.jpx.TrackSegment






@OptIn(ExperimentalSerializationApi::class)
class OpenStreetMap constructor(filename: String) {
    var cyclableGraph = GeographicGraph()

    /**
     * We put the RTree here because we don't want to have to bother with serializing them
     */

    var nodeTree: RTree<Long, Geometry> = RTree.star().maxChildren(30).create()
    var bulkNodeLoader : MutableList<Entry<Long, Geometry>> = mutableListOf<Entry<Long,Geometry>>()

    init {
        println("Parsing XML")
        parseXML(filename)
        println("Gathering weights")
        cyclableGraph.gatherWeights(nodeTree)
        println("Started pruning operation")
        cyclableGraph.pruneDisconnected(20915039)
        var longOne = -0.23811416735792498
        var latOne =   51.50894110094838
        var longTwo = -0.0718713518298268
        var latTwo = 51.51628156035058
        var startNode = nodeTree.nearest(Geometries.point(longOne,latOne),0.1,1).first().value()
        var endNode = nodeTree.nearest(Geometries.point(longTwo,latTwo),0.1,1).first().value()
        println("Started Route Finding")
        var startTime = System.nanoTime()
        var route = cyclableGraph.findRoute(startNode,endNode,10.0)
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
        nodeTree = nodeTree.add(node.attribute("id").value.toLong(), Geometries.point(longitude,latitude))
    }
    fun processWay(way : Element)
    {
        var oneWay = false
        var nodes = mutableListOf<Long>()
        val acceptedRoads = mutableListOf<String>("primary","secondary","tertiary","unclassified","residential","primary_link","secondary_link","tertiary_link","living_street","cycleway")
        var disallowedSurfaces = mutableListOf<String>("gravel","dirt","grass","pebblestone")
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
                if (subElement.attribute("k").value == "oneway" && subElement.attribute("v").value == "yes") oneWay = true
            }
        }
        for (i in 1..(nodes.size-1)) {
            cyclableGraph.addEdge(nodes[i-1],nodes[i],oneWay)
        }
        }
}