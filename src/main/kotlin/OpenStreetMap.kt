import com.github.davidmoten.rtree2.Entry
import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Geometry
import io.jenetics.jpx.GPX
import io.jenetics.jpx.Track
import io.jenetics.jpx.TrackSegment
import io.jenetics.jpx.WayPoint
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.dom4j.Element
import org.dom4j.io.SAXReader
import java.awt.SystemTray
import java.io.File
import java.nio.file.Path
import java.util.Collections.max
import java.util.Random


@OptIn(ExperimentalSerializationApi::class)
@Serializable
class OpenStreetMap constructor(val filename: String) {
    var cyclableGraph = GeographicGraph()
    init {
        if(cyclableGraph.vertices.size == 0) {
            println("Parsing XML")
            parseXML(filename)
            println("Creating rTree")
            for (node in cyclableGraph.vertices)
                cyclableGraph.nodeTree =
                    cyclableGraph.nodeTree.add(node.key, Geometries.point(node.value.longitude, node.value.latitude))
            println("Gathering weights")
            cyclableGraph.gatherWeights()
            cyclableGraph.pruneDisconnected(1964568424)
            for (node in cyclableGraph.safeNodes)
                cyclableGraph.vertices[node]?.weight = 0.0
        }
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
        var oneWay = false
        var cycleWay = false
        var slowWay = false
        var nodes = mutableListOf<Long>()
        val acceptedRoads =      mutableListOf<String>("bridleway","trunk","pedestrian","service","primary","secondary","tertiary","unclassified","residential","primary_link","secondary_link","tertiary_link","living_street","cycleway","footway")
        var disallowedSurfaces = mutableListOf<String>("unpaved","fine_gravel","gravel","dirt","grass","pebblestone")
        var disallowedAccess =   mutableListOf<String>("no")
        var highSpeed =          mutableListOf<String>("40 mph", "50 mph", "60 mph", "70 mph")
        var tags = HashMap<String,String>()
        val it = way.elementIterator()
        while(it.hasNext())
        {
            val subElement = it.next()
            if (subElement.qName.name == "nd") {
                nodes.add(subElement.attribute("ref").value.toLong())
            }
            if (subElement.qName.name == "tag")
            {
                tags[subElement.attributeValue("k")] = subElement.attributeValue("v")
            }
        }
        val highwayType = tags["highway"].toString()
        val access      = tags["access"].toString()
        val surface     = tags["surface"].toString()
        val note        = tags["note"].toString()
        val bicycle     = tags["bicycle"].toString()
        var oneway      = tags["oneway"].toString()
        var maxspeed    = tags["maxspeed"].toString()
        var towpath     = tags["towpath"].toString()
        var motor       = tags["motor_vehicle"].toString()
        if (!acceptedRoads.contains(highwayType)) return
        if (highSpeed.contains(maxspeed)) return
        if (oneway == "yes") oneWay = true
        if (highwayType == "cycleway") cycleWay = true
        if (highwayType == "footway") {
            cycleWay = true
            slowWay = true
        }
        if (highwayType == "pedestrian") {
            cycleWay = true
            if(bicycle!="designated")
            {
                slowWay = true
            }
        }
        if (note == "towpath") return
        if (disallowedAccess.contains(access)) return
        if (disallowedAccess.contains(bicycle)) return
        if (disallowedSurfaces.contains(surface)) return
        if (!acceptedRoads.contains(highwayType)) return
        if (towpath == "yes") return
        if (motor == "private") cycleWay = true
        if (cycleWay) {
            cyclableGraph.safeNodes.addAll(nodes)
        }
        if (slowWay) {
            cyclableGraph.slowNodes.addAll(nodes)
        }
        for (i in 1..(nodes.size-1)) {
            cyclableGraph.addEdge(nodes[i-1],nodes[i],oneWay)
        }
        }
}