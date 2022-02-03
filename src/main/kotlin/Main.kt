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
    saveFile.writeBytes(Cbor.encodeToByteArray(Graph))
    var read = saveFile.readBytes()
    var newGraph = Cbor.decodeFromByteArray<OpenStreetMap>(read)
    println("Decoded")
    var gpx = GPX.builder().build()
    gpx = newGraph.writeNewTrack(276492205,21672596,10.0,10.0,gpx)
    Graph.cyclableGraph.contractedGraph.findRoute(276492205,21672596,Graph.cyclableGraph)
    GPX.write(gpx,Path.of("route.gpx"))
}