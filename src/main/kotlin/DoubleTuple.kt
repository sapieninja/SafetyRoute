class DoubleTuple(val id : Long, val dist : Double) : Comparable<DoubleTuple>
{
    override fun compareTo(other: DoubleTuple): Int {
        if(dist > other.dist)
        {
            return 1
        }
        else if(dist == other.dist)
        {
            return 0
        }
        else
        {
            return -1
        }
    }
}