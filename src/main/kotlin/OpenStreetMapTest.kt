import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class OpenStreetMapTest {
    @Test
    fun createNewMap(){
        val map = OpenStreetMap("maps/ways.osm")
    }
}