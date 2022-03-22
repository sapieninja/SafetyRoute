import io.jenetics.jpx.GPX
import io.jenetics.jpx.Track
import io.jenetics.jpx.TrackSegment
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.dom4j.DocumentException
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.InvalidPathException
import java.nio.file.Path


//TODO implement contraction hierachies for hopeful speedup
//TODO add a gui
//TODO add location lookup
//TODO check findroute logic
@OptIn(ExperimentalSerializationApi::class)
fun writeNewTrack(route: List<Long>, cyclableGraph: GeographicGraph, gpx: GPX): GPX {
    var track: TrackSegment = TrackSegment.builder().build()
    for (point in route) {
        val node = cyclableGraph.vertices[point]
        track = track.toBuilder().addPoint { p -> p.lon(node!!.longitude).lat(node.latitude) }.build()
    }
    val segment: Track = Track.builder().addSegment(track).build()
    return gpx.toBuilder().addTrack(segment).build()
}

fun writeNewScatter(scatter: List<Long>, cyclableGraph: GeographicGraph, gpx: GPX): GPX {
    var workingGpx = gpx
    for (node in scatter) {
        val nodeVertice = cyclableGraph.vertices[node]!!
        workingGpx =
            workingGpx.toBuilder().addWayPoint { p -> p.lon(nodeVertice!!.longitude).lat(nodeVertice.latitude) }.build()
    }
    return workingGpx
}

@OptIn(ExperimentalSerializationApi::class)
fun writeObjectToDisk(filename: String, toWrite: OpenStreetMap) {
    val saveFile = File(filename)
    saveFile.writeBytes(Cbor.encodeToByteArray(toWrite))
}

@OptIn(ExperimentalSerializationApi::class)
fun readMapFromDisk(filename: String): OpenStreetMap {
    var readFile = File(filename)
    return Cbor.decodeFromByteArray(readFile.readBytes())
}

fun getMapObject(): OpenStreetMap {
    var map: OpenStreetMap
    while (true) {
        print("Load from osm file (o) or load from preprocessed data (p):")
        var input = readln()
        print("File name:")
        var filename = readln()
        if (input == "p") {
            try {
                map = readMapFromDisk(filename)
                println("Loaded map corresponding to ${map.filename}")
                println("This map has contractions for ${map.cyclableGraph.contractedGraphs.keys}")
                map.cyclableGraph.setupRTree()
            } catch (e: FileNotFoundException) {
                println("File not found please try a different file")
                continue
            } catch (e: DocumentException) {
                println("File is not of the right type, please fix or try a different file")
                continue
            }
            map.cyclableGraph.setupRTree() //re establish R*Tree
            break
        }
        if (input == "o") {
            try {
                map = OpenStreetMap(filename)
            } catch (e: FileNotFoundException) {
                println("File not found please try a different file")
                continue
            } catch (e: DocumentException)
            {
                println("File is not of the right type, please fix or try a different file")
                continue
            }
            break
        }
    }
    return map
}

@OptIn(ExperimentalSerializationApi::class)
fun main(args: Array<String>) {
    var gpx = GPX.builder().build()
    val map = getMapObject()
    while (true) {
        print("Contract (c), Find Route(r), Find Random Route (rr), Exit (e), Display Contraction Levels (d), Test (t) or Save(s):")
        var input = readln()
        when (input) {
            "e" -> break
            "d" -> println(map.cyclableGraph.contractedGraphs.keys)
            "s" -> {
                print("Save file path:")
                val filename = readln()
                try {
                    writeObjectToDisk(filename, map)
                } catch (e: InvalidPathException) {
                    continue
                }
            }
            "c" -> {
                print("Distance Cost:")
                try {
                    val distanceCost = readln().toDouble()
                    map.cyclableGraph.contractGraph(distanceCost)
                } catch (e: NumberFormatException) {
                    continue
                }
            }
            "r" -> {
                try {
                    print("Distance Cost:")
                    val distanceCost = readln().toDouble()
                    print("Turn Cost:")
                    val turnCost = readln().toDouble()
                    print("Coordinate One:")
                    val coordinateOne = readln()
                    print("Coordinate Two:")
                    val coordinateTwo = readln()
                    val latOne = coordinateOne.split(",")[0].toDouble()
                    val longOne= coordinateOne.split(",")[1].toDouble()
                    val latTwo = coordinateTwo.split(",")[0].toDouble()
                    val longTwo= coordinateTwo.split(",")[1].toDouble()
                    val startTime = System.nanoTime()
                    gpx=writeNewTrack(map.cyclableGraph.findRoute(latOne,longOne,latTwo,longTwo,distanceCost,turnCost,false), map.cyclableGraph, gpx)
                    val endTime = System.nanoTime()
                    println("Route finding completed in ${(endTime-startTime)/(1000000000.0)}")
                } catch(e : NumberFormatException){
                    continue
                }
            }
            "rr" -> {
                try {
                    print("Distance Cost:")
                    val distanceCost = readln().toDouble()
                    print("Turn Cost:")
                    val turnCost = readln().toDouble()
                    val first = map.cyclableGraph.getRandomId()
                    val second= map.cyclableGraph.getRandomId()
                    val startTime = System.nanoTime()
                    gpx=writeNewTrack(map.cyclableGraph.findRoute(first,second,distanceCost,turnCost,false), map.cyclableGraph, gpx)
                    val endTime = System.nanoTime()
                    println("Route finding completed in ${(endTime-startTime)/(1000000000.0)}")
                } catch(e : NumberFormatException){
                    continue
                }
            }
            "t" ->
            {
                //repeated query path between 2 far away nodes and record the average time difference
                val first = 68248591L
                val second = 117869324L
                print("Distance Cost:")
                val distanceCost = readln().toDouble()
                print("Turn Cost:")
                val turnCost = readln().toDouble()
                val startTime = System.nanoTime()
                for (i in 1..100)
                    map.cyclableGraph.findRoute(first,second,distanceCost,turnCost,false)
                val endTime = System.nanoTime()
                println("Route finding completed in average time of ${(endTime-startTime)/(1000000000.0*100.0)}")
            }
        }
    }
    print("Do you want to save the GPX file (y):")
    if (readln() == "y")
    {
        print("Filename:")
        GPX.write(gpx,Path.of(readln()))
    }
}
