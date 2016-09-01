package org.bbop.apollo.projection

import org.bbop.apollo.Organism

/**
 *
 * The class represents a single projected view.
 * Within that view, there are multiple ordered sequences (scaffolds).
 * Each sequence can represent the entire sequence, or a portion thereof.
 * Each sequence can have a reverse complement.
 * Each sequence can be repeated.
 * This allows an entire sequence region to be visualized.
 *
 * Within each sequence, only a portion thereof may shown due to a variety of folding.
 * This is represented by the DiscontinuousProjection.
 * The DiscontinuousProjection refer to viewed exons (or an entire unfolded transcript) within a scaffold region.
 *
 *
 * Created by nathandunn on 9/24/15.
 */
class MultiSequenceProjection extends AbstractProjection {

    // if a projection includes multiple sequences, this will include greater than one
    TreeMap<ProjectionSequence, DiscontinuousProjection> sequenceDiscontinuousProjectionMap = new TreeMap<>()

    List<String> chunks = new ArrayList<>()
    ProjectionChunkList projectionChunkList = new ProjectionChunkList()

    ProjectionSequence getReverseProjectionSequence(Integer input) {
        for (ProjectionSequence projectionSequence in sequenceDiscontinuousProjectionMap.keySet().sort() { a, b -> a.order <=> b.order }) {
            Integer bufferedLength = sequenceDiscontinuousProjectionMap.get(projectionSequence).bufferedLength
            if (input >= projectionSequence.offset && input <= projectionSequence.offset + bufferedLength) {
                return projectionSequence
            }
        }
        return null
    }

    List<ProjectionSequence> getReverseProjectionSequences(Integer minInput, Integer maxInput) {
        List<ProjectionSequence> orderedSequences = []
        Integer startOrder  = getReverseProjectionSequence(minInput)?.order
        Integer endOrder = getReverseProjectionSequence(maxInput)?.order
        if(endOrder==null ){
            endOrder = getLastSequence().order
        }

        for (ProjectionSequence projectionSequence in sequenceDiscontinuousProjectionMap.keySet().sort() { a, b -> a.order <=> b.order }) {
            if(projectionSequence.order>=startOrder && projectionSequence.order <= endOrder){
                orderedSequences << projectionSequence
            }
        }

        return orderedSequences
    }


    /**
     * Find which sequence I am on by iterating over coordinates
     * @param input
     * @return
     */
    ProjectionSequence getProjectionSequence(Integer input) {

        Integer offset = 0
        // should deliver these in order
        for (projectionSequence in sequenceDiscontinuousProjectionMap.keySet().sort() { a, b -> a.order <=> b.order }) {
            assert projectionSequence.unprojectedLength != null
            assert projectionSequence.unprojectedLength > 0
            int sequenceLength = projectionSequence.unprojectedLength
            if (input >= offset && input <= sequenceLength  + offset) {
                return projectionSequence
            }
            offset += sequenceLength
        }
        return null
    }

    Integer projectValue(Integer input,Integer inputOffset,Integer outputOffset) {
        ProjectionSequence projectionSequence = getProjectionSequence(input)
        if (!projectionSequence) {
            return UNMAPPED_VALUE
        }
        DiscontinuousProjection discontinuousProjection = sequenceDiscontinuousProjectionMap.get(projectionSequence)
        // TODO: buffer for scaffolds is currently 1 . . the order
        Integer returnValue = discontinuousProjection.projectValue(input - inputOffset)
        if (returnValue == UNMAPPED_VALUE) {
            return UNMAPPED_VALUE
        } else {
            return returnValue + outputOffset
        }
    }

    Integer projectValue(Integer input) {
        ProjectionSequence projectionSequence = getProjectionSequence(input)
        if (!projectionSequence) {
            return UNMAPPED_VALUE
        }
        return projectValue(input,projectionSequence.originalOffset,projectionSequence.offset)
    }


    Integer projectReverseValue(Integer input) {
        ProjectionSequence projectionSequence = getReverseProjectionSequence(input)
        if (!projectionSequence) return -1
        return sequenceDiscontinuousProjectionMap.get(projectionSequence).projectReverseValue(input - projectionSequence.offset) + projectionSequence.originalOffset
    }

    Integer getLength() {
        Map.Entry<ProjectionSequence, DiscontinuousProjection> entry = sequenceDiscontinuousProjectionMap.lastEntry()
        return entry.key.offset + entry.value.length
    }

    String projectSequence(String inputSequence, Integer minCoordinate, Integer maxCoordinate, Integer offset) {
        Integer index = 0
        List<String> sequenceList = []

        // we start at the very bottom and go up
        for(ProjectionSequence projectionSequence in sequenceDiscontinuousProjectionMap.keySet().sort(){a,b -> a.order<=>b.order }){
            DiscontinuousProjection discontinuousProjection = sequenceDiscontinuousProjectionMap.get(projectionSequence)
            Integer sequenceLength = projectionSequence.unprojectedLength
            offset = index

            // case 5: no overlap
            if(index > maxCoordinate || index+sequenceLength  < minCoordinate){
                // do nothing
                println "doing nothing with ${index}-${index+sequenceLength} in ${minCoordinate}-${maxCoordinate}"
            }
            // case 3: inbetween
            else
            if(minCoordinate > index && maxCoordinate < index + sequenceLength){
                sequenceList << discontinuousProjection.projectSequence(inputSequence,minCoordinate-index+offset, maxCoordinate - index+offset,offset)
            }
            // case 1: right edge
            else
            if(minCoordinate > index && maxCoordinate >= index + sequenceLength){
                sequenceList << discontinuousProjection.projectSequence(inputSequence,minCoordinate-index+offset, sequenceLength+offset,offset)
            }
            // case 2: left edge
            else
            if(minCoordinate <= index  && maxCoordinate < sequenceLength+index){
                sequenceList << discontinuousProjection.projectSequence(inputSequence,0+offset, maxCoordinate - index+offset,offset)
            }
            // case 4: overlap / all
            else
            if(minCoordinate <= index && maxCoordinate >= index + sequenceLength){
                sequenceList << discontinuousProjection.projectSequence(inputSequence,0+offset, sequenceLength+offset,offset)
            }
//            else{
//                throw new RuntimeException("Should not get here: ${minCoordinate},${maxCoordinate}")
//            }
            index += sequenceLength
        }

        // not really used .  .. .  but otherwise would carve up into different bits
        return sequenceList.join("")
    }

    List<Coordinate> listCoordinates() {
        List<Coordinate> coordinateList = new ArrayList<>()
        for (def projection in sequenceDiscontinuousProjectionMap.values()) {
            coordinateList.addAll(projection.minMap.values() as List<Coordinate>)
        }
        return coordinateList
    }

    def addInterval(int min, int max, ProjectionSequence sequence) {
        println "adding interval ${min} ${max} ${sequence}"
        Location location = new Location(min: min, max: max, sequence: sequence)
        addLocation(location)
    }


    Integer size() {
        Integer count = 0
        for (def projection in sequenceDiscontinuousProjectionMap.values()) {
            count += projection.size()
        }

        return count
    }

    def addLocations(List<Location> locationList) {
        for (Location location in locationList) {
            addLocation(location)
        }
    }

    @Override
    Integer clear() {
        int size = sequenceDiscontinuousProjectionMap.size()
        sequenceDiscontinuousProjectionMap.clear()
        return size
    }

// here we are adding a location to project
    def addLocation(Location location) {
        // if a single projection . . the default .. then assert that it is the same sequence / projection
//        ProjectionSequence projectionSequence = getProjectionSequence(location)
        DiscontinuousProjection discontinuousProjection = sequenceDiscontinuousProjectionMap.get(location.sequence)
        if (discontinuousProjection) {
            discontinuousProjection.addInterval(location.min, location.max, 0)
        } else {
            ProjectionSequence internalProjectionSequence = location.sequence

            Integer order = findSequenceOrder(internalProjectionSequence)
            internalProjectionSequence.order = order

            DiscontinuousProjection thisDiscontinuousProjection = new DiscontinuousProjection()
            thisDiscontinuousProjection.addInterval(location.min, location.max, 0)
            sequenceDiscontinuousProjectionMap.put(internalProjectionSequence, thisDiscontinuousProjection)
        }
    }

    Integer findSequenceOrder(ProjectionSequence projectionSequence) {
        // should return an ordered set
        List<ProjectionSequence> projectionSequenceList = sequenceDiscontinuousProjectionMap.keySet().sort() {a,b -> a.order <=> b.order }

//        List<ProjectionSequence> projectionSequenceList = sequenceList
        int index = 0
        for (ProjectionSequence projectionSequence1 in projectionSequenceList) {
            if (projectionSequence1.name == projectionSequence.name) {
                return index
            }
            ++index
        }
        return -1
    }


    /**
     * This is done at the end to make offsets render properly
     */
    def calculateOffsets() {
        Integer currentOrder = 0
        Integer lastLength = 0
        Integer originalLength = 0
        sequenceDiscontinuousProjectionMap.keySet().sort() { a, b -> a.order <=> b.order }.each {
            DiscontinuousProjection discontinuousProjection = sequenceDiscontinuousProjectionMap.get(it)
            if (currentOrder > 0) {
                it.offset = lastLength
                it.originalOffset = originalLength
            }

            lastLength += discontinuousProjection.bufferedLength
            assert it.unprojectedLength != null
            assert it.unprojectedLength > 0
            originalLength += it.unprojectedLength
            ++currentOrder
        }
    }

    ProjectionSequence getProjectionSequence(String sequenceName, Organism organism) {
        return getProjectionSequence(sequenceName, null, organism)
    }

    ProjectionSequence getProjectionSequence(String sequenceName, Long sequenceId, Organism organism) {
        for (ProjectionSequence projectionSequence in sequenceDiscontinuousProjectionMap.keySet()) {
            if (projectionSequence.name == sequenceName) {
                if (projectionSequence.organism && organism) {
                    if (projectionSequence.organism != organism.commonName) {
                        return projectionSequence
                    }
                }
                if (projectionSequence.id && sequenceId) {
                    if (projectionSequence.id != sequenceId) {
                        return null
                    }
                }
                return projectionSequence
            }
        }
        return null
    }

    Boolean containsSequence(String sequenceName, Organism organism) {
        return containsSequence(sequenceName, null, organism)
    }

    Boolean containsSequence(String sequenceName, Long sequenceId, Organism organism) {
        return getProjectionSequence(sequenceName, sequenceId, organism) != null
    }


    public String toString() {
        return "MultiSequenceProjection{" +
                "sequenceDiscontinuousProjectionMap=" + sequenceDiscontinuousProjectionMap +
                '}';
    }


    TreeMap<Integer, Coordinate> getMinMap() {
        Map<Integer, Coordinate> minMap = new TreeMap<>()
        List<ProjectionSequence> projectionSequenceList = sequenceDiscontinuousProjectionMap.keySet().sort() { a, b -> a.order <=> b.order } as List

        for (ProjectionSequence projectionSequence : projectionSequenceList) {
            Map<Integer,Coordinate>  returnMap = new TreeMap<>()

            sequenceDiscontinuousProjectionMap.get(projectionSequence).minMap.each {
                Coordinate coordinate = new Coordinate(min: it.value.min,max:it.value.max)
                coordinate.addOffset(projectionSequence.originalOffset)
                returnMap.put(it.key+projectionSequence.originalOffset,coordinate)
            }
            minMap.putAll(returnMap)
        }

        return minMap
    }

    TreeMap<Integer, Coordinate> getMaxMap() {
        Map<Integer, Coordinate> maxMap = new TreeMap<>()
        List<ProjectionSequence> projectionSequenceList = sequenceDiscontinuousProjectionMap.keySet().sort() { a, b -> a.order <=> b.order } as List

        for (ProjectionSequence projectionSequence : projectionSequenceList) {
            Map<Integer,Coordinate>  returnMap = new TreeMap<>()
            // add a set with an offset
            sequenceDiscontinuousProjectionMap.get(projectionSequence).maxMap.each {
                Coordinate coordinate = new Coordinate(min: it.value.min,max:it.value.max)
                coordinate.addOffset(projectionSequence.originalOffset)
                returnMap.put(it.key+projectionSequence.originalOffset,coordinate)
            }
            maxMap.putAll(returnMap)
        }

        return maxMap
    }

    Coordinate getMaxCoordinate(ProjectionSequence projectionSequence = null){
        if(projectionSequence==null){
            return getMaxMap().lastEntry().value
        }
        return sequenceDiscontinuousProjectionMap.get(projectionSequence).maxMap.lastEntry().value
    }

    Integer getOffsetForSequence(String sequenceName) {
        if (projectionChunkList) {
            ProjectionChunk projectionChunk = projectionChunkList.findProjectChunkForName(sequenceName)
            if (projectionChunk) {
                return projectionChunk.sequenceOffset
            }
        }
        for (ProjectionSequence projectionSequence in sequenceDiscontinuousProjectionMap.keySet()) {
            if (sequenceName == projectionSequence.name) {
                return projectionSequence.originalOffset
            }
        }
        return 0
    }

    ProjectionSequence getLastSequence() {
        return projectedSequences.last()
    }

    List<ProjectionSequence> getProjectedSequences(){
        List<ProjectionSequence> orderedSequences = sequenceDiscontinuousProjectionMap.keySet().sort(){a,b -> a.order <=> b.order }
        return orderedSequences
    }

    Map<String,Integer> getOrderedSequenceMap() {
        return getProjectedSequences().collectEntries { it ->
            [ (it.name) : it.order ]
        }
    }

    def addProjectionSequences(List<ProjectionSequence> theseProjectionSequences) {
        theseProjectionSequences.each {
            sequenceDiscontinuousProjectionMap.put(it,null)
        }
    }
}
