import com.github.davidmoten.rtree2.geometry.Geometries
import kotlinx.serialization.Serializable
import org.dom4j.Element
import org.dom4j.io.SAXReader
import java.io.File


@Serializable
class OpenStreetMap constructor(val filename: String) {
    var cyclableGraph = GeographicGraph()

    init {
        if (cyclableGraph.vertices.size == 0) {
            println("Parsing XML")
            parseXML(filename)
            println("Creating rTree")
            for (node in cyclableGraph.vertices)
                cyclableGraph.nodeTree =
                    cyclableGraph.nodeTree.add(node.key, Geometries.point(node.value.longitude, node.value.latitude))
            println("Gathering weights")
            cyclableGraph.gatherWeights()
            cyclableGraph.pruneDisconnected(1702430098)
            for (node in cyclableGraph.safeNodes)
                cyclableGraph.vertices[node]?.weight = 0.0
        }
    }

    private fun parseXML(filename: String) {
        val stream = File(filename).inputStream()
        val saxReader = SAXReader()
        val cyclableDocument = saxReader.read(stream)
        val root: Element = cyclableDocument.rootElement
        val it: Iterator<Element> = root.elementIterator()
        while (it.hasNext()) {
            val element: Element = it.next()
            when (element.qName.name) {
                "node" -> processNode(element)
                "way" -> processWay(element)
            }
        }
    }

    private fun processNode(node: Element) {
        val longitude = node.attribute("lon").value.toDouble()
        val latitude = node.attribute("lat").value.toDouble()
        cyclableGraph.vertices[node.attribute("id").value.toLong()] =
            GeographicGraph.GeographicNode(longitude, latitude)
    }

    private fun processWay(way: Element) {
        var oneWay = false
        var cycleWay = false
        var slowWay = false
        val nodes = mutableListOf<Long>()
        val acceptedRoads = mutableListOf(
            "bridleway",
            "trunk",
            "pedestrian",
            "service",
            "primary",
            "secondary",
            "tertiary",
            "unclassified",
            "residential",
            "primary_link",
            "secondary_link",
            "tertiary_link",
            "living_street",
            "cycleway",
            "footway"
        )
        val disallowedSurfaces = mutableListOf("unpaved", "fine_gravel", "gravel", "dirt", "grass", "pebblestone")
        val disallowedAccess = mutableListOf("no")
        val highSpeed = mutableListOf("40 mph", "50 mph", "60 mph", "70 mph")
        val tags = HashMap<String, String>()
        val it = way.elementIterator()
        while (it.hasNext()) {
            val subElement = it.next()
            if (subElement.qName.name == "nd") {
                nodes.add(subElement.attribute("ref").value.toLong())
            }
            if (subElement.qName.name == "tag") {
                tags[subElement.attributeValue("k")] = subElement.attributeValue("v")
            }
        }
        val highwayType = tags["highway"].toString()
        val access = tags["access"].toString()
        val surface = tags["surface"].toString()
        val note = tags["note"].toString()
        val bicycle = tags["bicycle"].toString()
        val oneway = tags["oneway"].toString()
        val maxspeed = tags["maxspeed"].toString()
        val towpath = tags["towpath"].toString()
        val motor = tags["motor_vehicle"].toString()
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
            if (bicycle != "designated") {
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
        for (i in 1 until nodes.size) {
            cyclableGraph.addEdge(nodes[i - 1], nodes[i], oneWay)
        }
    }
}