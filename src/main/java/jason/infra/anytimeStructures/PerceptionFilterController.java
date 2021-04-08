package jason.infra.anytimeStructures;

import jason.asSyntax.Literal;
import jason.infra.centralised.CentralisedEnvironment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

public class PerceptionFilterController {

    Collection<Literal> perceptions;
    Collection<Literal> delayedPerceptions = new ArrayDeque<>();
    PerceptionFilter pf;
    int currentFilterNumber = 1;
    int maxFilter = 1;
    HashMap<Integer,Method> filters = new HashMap<>();
    Method currentFilter;
    private static Logger logger = Logger.getLogger(CentralisedEnvironment.class.getName());

    public PerceptionFilterController(){

        try{
            this.pf = (PerceptionFilter) getClass().getClassLoader().loadClass("CustomPerceptionFilter").getConstructor().newInstance();
            System.out.println("Using CustomPerceptionFilter");
        }catch (Exception e) {
            e.printStackTrace();
            this.pf = new DefaultPerceptionFilter();
            System.out.println("Using DefaultPerceptionFilter");
        }

        Iterator<Method> iterator = Arrays.stream(pf.getClass().getDeclaredMethods()).iterator();

        while(iterator.hasNext()){
            Method method = iterator.next();
            int methodNumber = Integer.parseInt(method.getName().substring(6));
            if(methodNumber > maxFilter) maxFilter = methodNumber;
            filters.put(methodNumber,method);
        }

        System.out.println("maxFilter is "+maxFilter);
    }

    public void setPerceptions(Collection<Literal> perceptionList){
        this.perceptions = perceptionList;
        delayedPerceptions = new ArrayDeque<>();
        currentFilterNumber = 1;
        Method currentFilter = filters.get(1);
    }

    public boolean hasNext(){
        return !perceptions.isEmpty() || !delayedPerceptions.isEmpty();
    }

    public Literal getPerception(){
        if(!perceptions.isEmpty()){
            if(currentFilterNumber>maxFilter){
                Literal p = perceptions.stream().findFirst().get();
                perceptions.remove(p);
                return p;
            }
            Method currentFilter = filters.get(currentFilterNumber);
            Literal nextPercept = perceptions.stream().findFirst().get();
            perceptions.remove(nextPercept);
            try {
                if (Boolean.parseBoolean(currentFilter.invoke(this.pf,nextPercept).toString())) {
                    return nextPercept;
                } else {
                    delayedPerceptions.add(nextPercept);
                    return getPerception();
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }else{
            if (!delayedPerceptions.isEmpty()){
                perceptions = delayedPerceptions;
                delayedPerceptions = new LinkedList<>();
                currentFilterNumber++;
                if(currentFilterNumber<=maxFilter){
                    currentFilter = filters.get(currentFilterNumber);
                }else{
                }
                return getPerception();
            }
        }

        return null;
    }
}
