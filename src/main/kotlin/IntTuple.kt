class IntTuple(val id : Long, val dist : Int) : Comparable<IntTuple>
{
    override fun compareTo(other: IntTuple): Int {
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