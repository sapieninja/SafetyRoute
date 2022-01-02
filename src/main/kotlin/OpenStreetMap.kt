import com.wolt.osm.parallelpbf.ParallelBinaryParser
import com.wolt.osm.parallelpbf.entity.Node
import com.wolt.osm.parallelpbf.entity.Relation
import com.wolt.osm.parallelpbf.entity.Way
import org.slf4j.LoggerFactory
import java.io.File

class OpenStreetMap constructor(filename: String){
    val cyclableGraph = GeographicGraph()
    init{
        var stream = File(filename).inputStream()
        var x = ParallelBinaryParser(stream,1).onNode(this::processNode).onWay(this::processWay).parse()
        println(cyclableGraph.nodeTree.asString())
        cyclableGraph.gatherWeights()
    }
    private fun processNode(node: Node)
    {
       cyclableGraph.addNode(node)
    }
    private fun processWay(way: Way){
        cyclableGraph.addEdge(way.nodes.first(),way.nodes.last())
    }
}