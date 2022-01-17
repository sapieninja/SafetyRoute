import io.jenetics.jpx.GPX
import java.nio.file.Path

fun main(args: Array<String>) {
    var Graph = OpenStreetMap("maps/ways.osm")
    var gpx = GPX.builder().build()
    gpx = Graph.writeNewTrack(1136307406,2377118,10.0,10.0,gpx)
    GPX.write(gpx, Path.of("route.gpx"))
}