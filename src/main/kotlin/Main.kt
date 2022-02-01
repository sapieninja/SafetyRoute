import io.jenetics.jpx.GPX
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import java.io.File
import java.nio.file.Path
import java.util.*

fun main(args: Array<String>) {
    var saveFile = File("savedGraph.bin")
    var read = saveFile.readBytes()
    var newGraph = Cbor.decodeFromByteArray<OpenStreetMap>(read)
    newGraph.cyclableGraph.setup()
    println("Decoded")
    var gpx = GPX.builder().build()
    gpx = newGraph.writeNewTrack(newGraph.cyclableGraph.getRandomId(),newGraph.cyclableGraph.getRandomId(),10.0,10.0,gpx)
    GPX.write(gpx,Path.of("route.gpx"))
}