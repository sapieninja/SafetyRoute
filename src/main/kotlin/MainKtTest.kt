import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class MainKtTest {

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun writeMapToDisk() {
        val map = OpenStreetMap("maps/testarea.osm")
        writeMapToDisk("/tmp/savedMap.bin",map)
        var newMap = readMapFromDisk("/tmp/savedMap.bin")
        assertEquals(map.cyclableGraph.vertices.size,newMap.cyclableGraph.vertices.size)
    }

    @Test
    fun readMapFromDisk() {
        val map = readMapFromDisk("savedGraph.bin")
        map.cyclableGraph.findRoute(map.cyclableGraph.getRandomId(),map.cyclableGraph.getRandomId(),10.0,2.0,false)
    }

}