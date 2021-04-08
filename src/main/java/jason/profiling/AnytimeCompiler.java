package jason.profiling;

import org.apache.commons.math3.random.EmpiricalDistribution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AnytimeCompiler {

    private AnytimeProfiler ap = null;
//    private HashMap<String,ComponentAllocation> allocationMap = null;
    private HashMap<String, EmpiricalDistribution> distributionMap = new HashMap<>();
    private HashMap<String,Long> allocationTimeMap = new HashMap<>();
    private ArrayList<ComponentAllocation> allocationMap = new ArrayList<>();

    public AnytimeCompiler(AnytimeProfiler ap){
        this.ap = ap;
        for(Map.Entry<String, ArrayList<Long>> entry : ap.getTimeMap().entrySet()) {
            String key = entry.getKey();
            System.out.println(key);
            ArrayList<Long> value = entry.getValue();
            EmpiricalDistribution ep = new EmpiricalDistribution(value.size());
            ep.load(value.stream().mapToDouble(d->d).toArray());
            distributionMap.put("time"+key,ep);
        }
        for(Map.Entry<String, ArrayList<Integer>> entry : ap.getOccurrenceMap().entrySet()) {
            String key = entry.getKey();
            ArrayList<Integer> value = entry.getValue();

            EmpiricalDistribution ep = new EmpiricalDistribution(value.size());
            ep.load(value.stream().mapToDouble(d->d).toArray());
            distributionMap.put("occurrence"+key,ep);
        }
        for(Map.Entry<String, EmpiricalDistribution> entry : distributionMap.entrySet()) {
            System.out.println(entry.getKey());
        }
    }

    public void calculateExecutionTimes(int responseTimeMilis) {

        //perceptions
        double timepercept = distributionMap.get("timepercept").getNumericalMean();
        double occurrencepercepts = distributionMap.get("occurrencepercepts").inverseCumulativeProbability(0.95);
        double timeclearPercepts = distributionMap.get("timeclearPercepts").getNumericalMean();
        allocationMap.add(new ComponentAllocation("perception",occurrencepercepts,timepercept,timeclearPercepts));

        //messages
        double timemessage = distributionMap.get("timemessage").getNumericalMean();
        double occurrencemessages = distributionMap.get("occurrencemessages").inverseCumulativeProbability(0.95);
        double timecheckMail = distributionMap.get("timecheckMail").getNumericalMean();
        allocationMap.add(new ComponentAllocation("message",occurrencemessages,timemessage,timecheckMail));

        //event
        double timeevent = distributionMap.get("timeevent").getNumericalMean();
        double occurrenceevents = distributionMap.get("occurrenceevents").inverseCumulativeProbability(0.95);
        allocationMap.add(new ComponentAllocation("event",occurrenceevents,timeevent,0));

        //act
        //get time to execute
        double timeaction = distributionMap.get("timeaction").getNumericalMean();
        //get how many actions are needed
        distributionMap.get("occurrencaction");

    }

    private static double overallQuality(ArrayList<ComponentAllocation> allocations){
        double qual = 1;
        for (int i = 0; i < allocations.size(); i++) {
            qual*= allocations.get(i).quality;
        }
        return qual;
    }

    private class ComponentAllocation {
        private String componentName;
        private long timeAllocated;
        private double quality;
        private double occurence;
        private double averageTime;
        private double previousCost;

        public ComponentAllocation(String componentName, double occurrence, double averageTime, double previousCost){
            this.componentName = componentName;
            this.occurence = occurrence;
            this.averageTime = averageTime;
            this.timeAllocated = 0;
            this.previousCost = previousCost;
            calculateQuality();
        }

        public void incrementTimeAllocated(long timeAllocated){
            this.timeAllocated += timeAllocated;
            calculateQuality();
        }

        public double getQuality(){
            return quality;
        }

        public String getComponentName(){
            return componentName;
        }

        public void calculateQuality(){
            if (componentName.equals("actions")){
                quality = distributionMap.get("occurrencaction").cumulativeProbability(timeAllocated/averageTime);

            }else{
                if(occurence==0)
                    quality=100;
                else
                    quality = (100*((timeAllocated-previousCost)/averageTime))/occurence;
                    if(quality>100)quality=100;
            }
        }
    }
}
