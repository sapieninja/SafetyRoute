import io.jenetics.jpx.GPX
import io.jenetics.jpx.Track
import io.jenetics.jpx.TrackSegment
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import java.io.File
import java.nio.file.Path
import java.util.LinkedList
import java.util.Queue

//TODO implement contraction hierachies for hopeful speedup
//TODO add a gui
//TODO add location lookup
//TODO check findroute logic
@OptIn(ExperimentalSerializationApi::class)
fun writeNewTrack(route : List<Long>, cyclableGraph : GeographicGraph, gpx : GPX): GPX {
    var track: TrackSegment = TrackSegment.builder().build()
    for (point in route) {
        var node = cyclableGraph.vertices[point]
        track = track.toBuilder().addPoint { p -> p.lon(node!!.longitude).lat(node.latitude) }.build()
    }
    var segment: Track = Track.builder().addSegment(track).build()
    return gpx.toBuilder().addTrack(segment).build()
}
@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    var saveFile = File("savedGraph.bin")
    var newGraph : OpenStreetMap = Cbor.decodeFromByteArray(saveFile.readBytes())
    //var newGraph = OpenStreetMap("maps/ways.osm")
    //newGraph.cyclableGraph.contractGraph(newGraph.cyclableGraph)
    println("Decoded")
    var gpx = GPX.builder().build()
    var first = newGraph.cyclableGraph.getRandomId()
    var second =newGraph.cyclableGraph.getRandomId()
    println(first)
    println(second)
    gpx = writeNewTrack(
            newGraph.cyclableGraph.findRoute(first, second, 10.0, 0.0, false),
            newGraph.cyclableGraph,
            gpx
        )
    for (node in newGraph.cyclableGraph.findRoute(first, second, 10.0, 0.0, true)) {
            var nodeVertice = newGraph.cyclableGraph.vertices[node]
            gpx =
                gpx.toBuilder().addWayPoint { p -> p.lon(nodeVertice!!.longitude).lat(nodeVertice.latitude) }.build()
        }
    newGraph.cyclableGraph.isContracted = false
    gpx = writeNewTrack(
            newGraph.cyclableGraph.findRoute(first, second, 10.0, 0.0, false),
            newGraph.cyclableGraph,
            gpx
        )
    GPX.write(gpx, Path.of("route.gpx"))
    newGraph.cyclableGraph.isContracted = true
    saveFile.writeBytes(Cbor.encodeToByteArray(newGraph))
}
