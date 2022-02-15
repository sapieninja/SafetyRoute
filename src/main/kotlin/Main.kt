import io.jenetics.jpx.GPX
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import java.io.File
import java.nio.file.Path
import java.util.*

//TODO implement contraction hierachies for hopeful speedup
//TODO add a gui
//TODO add location lookup
@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    var Graph = OpenStreetMap("maps/ways.osm")
    Graph.cyclableGraph.contractGraph()
    var saveFile = File("savedGraph.bin")
    var newGraph = Graph
    println("Decoded")
    var gpx = GPX.builder().build()
    gpx = newGraph.writeNewTrack(276492205, 68343918, 10.0, 10.0, gpx)
    newGraph.cyclableGraph.isContracted = false
    gpx = newGraph.writeNewTrack(276492205, 68343918, 10.0, 10.0, gpx)
    GPX.write(gpx, Path.of("route.gpx"))
}
