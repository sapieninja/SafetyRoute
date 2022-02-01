import io.jenetics.jpx.GPX
import java.nio.file.Path
import java.util.*

fun main(args: Array<String>) {
    var Graph = OpenStreetMap("maps/ways.osm")
    var gpx = GPX.builder().build()
    for (i in 1..10)  gpx = Graph.writeNewTrack(Graph.cyclableGraph.getRandomId(),Graph.cyclableGraph.getRandomId(),10.0,10.0,gpx)
    GPX.write(gpx, Path.of("route.gpx"))
}