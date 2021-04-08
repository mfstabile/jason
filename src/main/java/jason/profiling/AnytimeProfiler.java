package jason.profiling;

import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class AnytimeProfiler implements Serializable {

    private HashMap<String, ArrayList<Long>> timeMap = new HashMap<String, ArrayList<Long>>(20);
    private HashMap<String, ArrayList<Integer>> occurrenceMap = new HashMap<String, ArrayList<Integer>>(20);

//    private HashMap<String,ComponentAllocation> allocationMap = null;
//    private HashMap<String,Long> allocationTimeMap = null;

    private String agName;

    private boolean loaded = false;

    public AnytimeProfiler(String agName){
        this.agName = agName;
    }

    public void addTime(String s, long time) {
        ArrayList<Long> ar = timeMap.get(s);
        if (ar==null) {
            ar = new ArrayList<Long>();
            timeMap.put(s, ar);
        }
        ar.add(time);
    }

    public void addOccurrence(String s, int occurrence) {
        ArrayList<Integer> ar = occurrenceMap.get(s);
        if (ar==null) {
            ar = new ArrayList<Integer>();
            occurrenceMap.put(s, ar);
        }
        ar.add(occurrence);
    }

    public String getJson(){
        Gson gson = new Gson();
        String json = gson.toJson(this);
        return json;
    }

    public String toString(){
        String text = "";
        text += "Times:\n";
        for (Map.Entry<String, ArrayList<Long>> entry : timeMap.entrySet()) {

            text += entry.getKey() + "\n";

            Iterator<Long> timeIterator = entry.getValue().iterator();
            while (timeIterator.hasNext()) {
                text += timeIterator.next() + ",";
            }
            text += "\n-----------------------------\n";
        }
        text += "\nOccurences:\n";
        for (Map.Entry<String, ArrayList<Integer>> entry : occurrenceMap.entrySet()) {

            text += entry.getKey() + "\n";

            Iterator<Integer> occIterator = entry.getValue().iterator();
            while (occIterator.hasNext()) {
                text += occIterator.next() + ",";
            }
            text += "\n-----------------------------\n";
        }

        return text;
    }

    public void saveToFile(){
        String str = getJson();
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter("profiling/"+agName+".json"));
            writer.write(str);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public HashMap<String, ArrayList<Long>> getTimeMap() {
        return timeMap;
    }

    public HashMap<String, ArrayList<Integer>> getOccurrenceMap() {
        return occurrenceMap;
    }

    public static AnytimeProfiler loadJson(String agName) throws IOException {

        String content = "";
        content = new String ( Files.readAllBytes( Paths.get("profiling/"+agName+".json") ) );
        Gson gson = new Gson();
        AnytimeProfiler ap = gson.fromJson(content,AnytimeProfiler.class);
        return ap;
    }

    public static AnytimeProfiler createProfiler(String agName){
        AnytimeProfiler ap;
        try{
            ap = loadJson(agName);
            ap.loaded = true;
        }catch (Exception e){
            ap = new AnytimeProfiler(agName);
        }
        return ap;
    }

    public boolean isLoaded(){
        return loaded;
    }

//    public long getTimePercentileValue(String key, double percentile){
//        ArrayList<Long> longs = timeMap.get(key);
//        if(longs == null || longs.isEmpty()){
//            System.out.println(agName+"] Not enough records of " + key);
//            return 1;
//        }
//        EmpiricalDistribution ed = new EmpiricalDistribution(longs.size());
//        ed.load(longs.stream().mapToDouble(d->d).toArray());
//        return (long) ed.inverseCumulativeProbability(percentile/100.0);
//
////        int index = (int) Math.ceil(percentile / 100.0 * longs.size());
////        return longs.get(index-1);
//    }
//
//    public int getOccurencePercentileValue(String key, double percentile){
//        ArrayList<Integer> integers = occurrenceMap.get(key);
//        if(integers == null || integers.isEmpty()) return 0;
////        Collections.sort(integers);
////        int index = (int) Math.ceil(percentile / 100.0 * integers.size());
////        return integers.get(index-1);
//        EmpiricalDistribution ed = new EmpiricalDistribution(integers.size());
//        ed.load(integers.stream().mapToDouble(d->d).toArray());
//        return (int) ed.inverseCumulativeProbability(percentile/100.0);
//    }
//
//    public double getOccurencePercentile(String key, int value){
//        ArrayList<Integer> integers = occurrenceMap.get(key);
//        try {
//            EmpiricalDistribution ed = new EmpiricalDistribution(integers.size());
//            ed.load(integers.stream().mapToDouble(d->d).toArray());
//            return (int) ed.cumulativeProbability(value)*100;
//        }catch (Exception e){
//            System.out.println("["+agName+"] Not enough records of " + key);
//            if(integers == null || integers.isEmpty())return 100.0;
//            else return integers.get(integers.size()-1);
//        }
//    }

//    public void calculateExecutionTimes(long responseTimeMillis){
//
////        System.out.println("calculateExecutionTimes1");
////        //get extAct time 95%
////        long externalExecutionTime = getTimePercentileValue("executeExternal",95.0);
////
////        long nanoTotalTime = responseTimeMillis * 1000000;
////        long allocationTime = (nanoTotalTime - externalExecutionTime)/4;
////
////        if (allocationTime <= 0) {
////            System.out.println("["+agName+"] There is not enough time to allocate");
////            allocationTime = 1;
////        }
////
////        ArrayList<ComponentAllocation> allocations = new ArrayList<>();
////        System.out.println("calculateExecutionTimes2");
////        //get 95% of buf, msg, event
////        try {
////            int perceptOccurrence = getOccurencePercentileValue("percepts", 95.0);//how many percepts are updated each cycle.
////            long perceptAverageTime = getTimePercentileValue("addPercept", 50);//average time for 1 percept update
////            long perceptClearTime = 0;//Time for cleaning the belief base
////            allocations.add(new ComponentAllocation("percepts", perceptOccurrence, (int) perceptAverageTime, allocationTime, (int) perceptClearTime));
////
////            int messageOccurrence = getOccurencePercentileValue("messages", 95.0);//how many messages are updated each cycle.
////            long messageAverageTime = getTimePercentileValue(TransitionSystem.State.StartRC.toString(), 50);//average time for 1 message parsing
////            long mailCheckTime = getTimePercentileValue("checkMail", 50);//Time for checking mail
////            allocations.add(new ComponentAllocation("messages", messageOccurrence, (int) messageAverageTime, allocationTime, (int) mailCheckTime));
////
////            int eventOccurrence = getOccurencePercentileValue("events", 95.0);//how many events are generated each cycle.
////            long eventAverageTime = getTimePercentileValue("event", 50);//average time for 1 event
////            allocations.add(new ComponentAllocation("events", eventOccurrence, (int) eventAverageTime, allocationTime, 0));
////
////            //calc prob of external act after ActAllocTime
////            long actionAverageTime = getTimePercentileValue("action", 50);//average time for 1 action
////            allocations.add(new ComponentAllocation("actions", 0, (int) actionAverageTime, allocationTime, 0));
////
////        }catch (Exception e){
////            e.printStackTrace();
////            System.out.println("AnytimeProfiler Exception " + e.toString());
////            throw e;
////        }
////        System.out.println("calculateExecutionTimes3");
////        //hill climb allocTime on buf, msg, event, act
////
////        long timeStep = allocationTime/10;
////
////        boolean changed = false;
////        while (timeStep>10){
////            for (int i = 0; i < allocations.size(); i++) {
////                for (int j = 0; j < allocations.size(); j++) {
////                    if(i==j)continue;
////
////                    double currentQuality = overallQuality(allocations);
////
////                    allocations.get(i).incrementTimeAllocated(timeStep);
////                    allocations.get(j).incrementTimeAllocated(-timeStep);
////
////                    double newQuality = overallQuality(allocations);
////                    if(newQuality > currentQuality && allocations.get(i).timeAllocated > 0 && allocations.get(j).timeAllocated > 0){
////                        changed=true;
////                    }else{
////                        allocations.get(i).incrementTimeAllocated(-timeStep);
////                        allocations.get(j).incrementTimeAllocated(timeStep);
////                    }
////                }
////            }
////            if(!changed){
////                timeStep/=10;
////            }else{
////                changed=false;
////            }
////        }
////        System.out.println("calculateExecutionTimes4");
////        allocationMap = new HashMap();
////        allocationTimeMap = new HashMap();
////        for (int i = 0; i < allocations.size(); i++) {
////            ComponentAllocation c = allocations.get(i);
////            allocationMap.put(c.componentName,c);
////            allocationTimeMap.put(c.componentName,c.timeAllocated);
////        }
////
////        printExecutionInformation();
//
////        Scanner in = new Scanner(System.in);
////        String s = in.nextLine();
//
//    }

//    private void printExecutionInformation(){
//        if (allocationMap != null){
//            System.out.println(agName+"] ---------------- Anytime Execution Information ----------");
//            for (Map.Entry<String, ComponentAllocation> entry : allocationMap.entrySet())
//                System.out.println(agName+ "]" + entry.getKey() + ", AllocTime: " + entry.getValue().timeAllocated+ ", ExpQuality: " + entry.getValue().quality);
//            System.out.println(agName+"] ---------------- End Anytime Execution Information ----------");
//        }
//    }
}
