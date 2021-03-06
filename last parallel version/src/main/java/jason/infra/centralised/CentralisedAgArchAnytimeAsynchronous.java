package jason.infra.centralised;

import jason.JasonException;
import jason.ReceiverNotFoundException;
import jason.architecture.AgArch;
import jason.asSemantics.*;
import jason.asSyntax.Atom;
import jason.asSyntax.Literal;
import jason.mas2j.ClassParameters;
import jason.runtime.RuntimeServices;
import jason.runtime.RuntimeServicesFactory;
import jason.runtime.Settings;
import jason.util.Config;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides an agent architecture when using Centralised
 * infrastructure to run the MAS inside Jason.
 *
 * Each agent has its own thread.
 *
 * <p>
 * Execution sequence:
 * <ul>
 * <li>initAg,
 * <li>setEnvInfraTier,
 * <li>setControlInfraTier,
 * <li>run (perceive, checkMail, act),
 * <li>stopAg.
 * </ul>
 */
public class CentralisedAgArchAnytimeAsynchronous extends CentralisedAgArch implements Runnable, Serializable {

    private static final long serialVersionUID = 4378889704809002271L;

    protected transient CentralisedEnvironment      infraEnv     = null;
    private   transient CentralisedExecutionControl infraControl = null;
    private   transient BaseCentralisedMAS          masRunner    = BaseCentralisedMAS.getRunner();

    private String             agName  = "";
    private volatile boolean   running = true;
    private Queue<Message>     mbox    = new ConcurrentLinkedQueue<>();
    protected transient Logger logger  = Logger.getLogger(CentralisedAgArchAnytimeAsynchronous.class.getName());

    ExecutorService executor = Executors.newFixedThreadPool(3);
    ActionExec defaultAction;
    private int responseTimeMilis;
    private double probabilityDistributionThreshold;

    private static List<MsgListener> msgListeners = null;
    public static void addMsgListener(MsgListener l) {
        if (msgListeners == null) {
            msgListeners = new ArrayList<>();
        }
        msgListeners.add(l);
    }
    public static void removeMsgListener(MsgListener l) {
        msgListeners.remove(l);
    }

    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        inputStream.defaultReadObject();
        sleepSync   = new Object();
        syncMonitor = new Object();
        masRunner   = BaseCentralisedMAS.getRunner();
    }


    /**
     * Creates the user agent architecture, default architecture is
     * jason.architecture.AgArch. The arch will create the agent that creates
     * the TS.
     */
    public void createArchs(Collection<String> agArchClasses, String agClass, ClassParameters bbPars, String asSrc, Settings stts) throws Exception {
        try {
            Agent.create(this, agClass, bbPars, asSrc, stts);
            insertAgArch(this);

            createCustomArchs(agArchClasses);

            // mind inspector arch
            if (stts.getUserParameter(Settings.MIND_INSPECTOR) != null) {
                insertAgArch( (AgArch)Class.forName( Config.get().getMindInspectorArchClassName()).getConstructor().newInstance() );
                getFirstAgArch().init();
            }

            setLogger();
        } catch (Exception e) {
            running = false;
            throw e; //new JasonException("as2j: error creating the agent class! - "+e.getMessage(), e);
        }
    }

    /** init the agent architecture based on another agent */
    public void createArchs(Collection<String> agArchClasses, Agent ag) throws JasonException {
        try {
            setMASRunner(masRunner); // TODO: remove
            setTS(ag.clone(this).getTS());
            insertAgArch(this);

            createCustomArchs(agArchClasses);

            setLogger();
        } catch (Exception e) {
            running = false;
            throw new JasonException("as2j: error creating the agent class! - ", e);
        }
    }

    public void setMASRunner(BaseCentralisedMAS masRunner) {
        this.masRunner = masRunner;
    }


    public void stopAg() {
        running = false;
        wake(); // so that it leaves the run loop
        if (myThread != null)
            myThread.interrupt();
        getTS().getAg().stopAg();

        // stop all archs
        AgArch f = getUserAgArch();
        while (f != null) {
            f.stop();
            f = f.getNextAgArch();
        }
    }


    public void setLogger() {
        logger = Logger.getLogger(CentralisedAgArchAnytimeAsynchronous.class.getName() + "." + getAgName());
        if (getTS().getSettings().verbose() >= 0)
            logger.setLevel(getTS().getSettings().logLevel());
    }

    public Logger getLogger() {
        return logger;
    }

    public void setAgName(String name) throws JasonException {
        if (name.equals("self"))
            throw new JasonException("an agent cannot be named 'self'!");
        if (name.equals("percept"))
            throw new JasonException("an agent cannot be named 'percept'!");
        agName = name;
    }

    public String getAgName() {
        return agName;
    }

    /**
     *
     * @deprecated use getFirstAgArch instead
     */
    public AgArch getUserAgArch() {
        return getFirstAgArch();
    }

    public void setEnvInfraTier(CentralisedEnvironment env) {
        infraEnv = env;
    }

    public CentralisedEnvironment getEnvInfraTier() {
        return infraEnv;
    }

    public void setControlInfraTier(CentralisedExecutionControl pControl) {
        infraControl = pControl;
    }

    public CentralisedExecutionControl getControlInfraTier() {
        return infraControl;
    }

    public void setDefaultAction(ActionExec defaultAction) {
        this.defaultAction = defaultAction;
    }

//    public int getResponseTimeMilis() {
//        return responseTimeMilis;
//    }

    public void setResponseTimeMilis(int responseTimeMilis) {
        this.responseTimeMilis = responseTimeMilis;
    }

//    public double getProbabilityDistributionThreshold() {
//        return probabilityDistributionThreshold;
//    }

    public void setProbabilityDistributionThreshold(double probabilityDistributionThreshold) {
        this.probabilityDistributionThreshold = probabilityDistributionThreshold;
    }

    private transient Thread myThread = null;
    public void setThread(Thread t) {
        myThread = t;
        myThread.setName(agName);
    }
    public Thread getThread() {
        return myThread;
    }
    public void startThread() {
        myThread.start();
    }

    public boolean isRunning() {
        return running;
    }

    Callable<Boolean> senseRunnable = () -> {
        TransitionSystem ts = getTS();
        ts.anytimeSense();
        return true;
    };
    Callable<Boolean> deliberateRunnable = () -> {
        TransitionSystem ts = getTS();
        ts.anytimeDeliberate();
        return true;
    };
    Callable<Boolean> actRunnable = () -> {
        TransitionSystem ts = getTS();
        ts.anytimeAct();
        return true;
    };

    Callable<Boolean> senseRunnableProfiler = () -> {
        TransitionSystem ts = getTS();
        ts.anytimeSenseProfiler();
        return true;
    };
    Callable<Boolean> deliberateRunnableProfiler = () -> {
        TransitionSystem ts = getTS();
        ts.anytimeDeliberateProfiler();
        return true;
    };
    Callable<Boolean> actRunnableProfiler = () -> {
        TransitionSystem ts = getTS();
        ts.anytimeActProfiler();
        return true;
    };


    protected void profilingReasoningCycle() {

        Future<Boolean> futureSense = executor.submit(senseRunnableProfiler);
        Future<Boolean> futureDeliberate = executor.submit(deliberateRunnableProfiler);
        Future<Boolean> futureAct = executor.submit(actRunnableProfiler);
        try {
            futureSense.get();
            futureDeliberate.get();
            futureAct.get();

            DelayedAction da = getTS().getC().getAPQ().poll();
            if (da != null) {
                ActionExec action = da.getAction();
                getTS().getC().addPendingAction(action);
                act(action);
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    protected void reasoningCycle() {

        //get time
        getTS().setResumeAnytimeExecution(true);

//        Future<Boolean> futureSense = executor.submit(senseRunnable);
//        Future<Boolean> futureDeliberate = executor.submit(deliberateRunnable);
//        Future<Boolean> futureAct = executor.submit(actRunnable);

//        sleep time - something
//        try {
//            Thread.sleep(responseTimeMilis);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        //send message
//        getTS().setResumeAnytimeExecution(false);

        try {
            Future<Boolean> futureSense = executor.submit(senseRunnableProfiler);
            futureSense.get();
            Future<Boolean> futureDeliberate = executor.submit(deliberateRunnable);
            futureDeliberate.get();
            Future<Boolean> futureAct = executor.submit(actRunnable);
            futureAct.get();

            DelayedAction da = getTS().getC().getAPQ().poll();
            if (da != null) {
                ActionExec action = da.getAction();

                getTS().getC().addPendingAction(action);
                // We need to send a wrapper for FA to the user so that add method then calls C.addFA (which control atomic things)
                act(action);
            }
//            else{
//                act(defaultAction);
//            }

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        TransitionSystem ts = getTS();
        while (running) {
            if (ts.getSettings().isSync()) {
                waitSyncSignal();
                reasoningCycle();
                boolean isBreakPoint = false;
                try {
                    isBreakPoint = ts.getC().getSelectedOption().getPlan().hasBreakpoint();
                    if (logger.isLoggable(Level.FINE)) logger.fine("Informing controller that I finished a reasoning cycle "+getCycleNumber()+". Breakpoint is " + isBreakPoint);
                } catch (NullPointerException e) {
                    // no problem, there is no sel opt, no plan ....
                }
                informCycleFinished(isBreakPoint, getCycleNumber());
            } else {
                getFirstAgArch().incCycleNumber(); // should not increment in case of sync execution
                reasoningCycle();
            }
        }
        logger.fine("I finished!");
    }

    private transient Object sleepSync = new Object();
    private int    sleepTime = 50;

    public static final int MAX_SLEEP = 100;

    public void sleep() {
        try {
            if (!getTS().getSettings().isSync()) {
                //logger.fine("Entering in sleep mode....");
                synchronized (sleepSync) {
                    sleepSync.wait(sleepTime); // wait for messages
                    if (sleepTime < MAX_SLEEP)
                        sleepTime += 100;
                }
            }
        } catch (InterruptedException e) {
        } catch (Exception e) {
            logger.log(Level.WARNING,"Error in sleep.", e);
        }
    }

    @Override
    public void wake() {
        synchronized (sleepSync) {
            sleepTime = 50;
            sleepSync.notifyAll(); // notify sleep method
        }
    }

    @Override
    public void wakeUpSense() {
        wake();
    }

    @Override
    public void wakeUpDeliberate() {
        wake();
    }

    @Override
    public void wakeUpAct() {
        wake();
    }

    // Default perception assumes Complete and Accurate sensing.
    @Override
    public Collection<Literal> perceive() {
        super.perceive();
        if (infraEnv == null) return null;
        Collection<Literal> percepts = infraEnv.getUserEnvironment().getPercepts(getAgName());
        if (logger.isLoggable(Level.FINE) && percepts != null) logger.fine("percepts: " + percepts);
        return percepts;
    }

    // this is used by the .send internal action in stdlib
    public void sendMsg(Message m) throws ReceiverNotFoundException {
        // actually send the message
        if (m.getSender() == null)  m.setSender(getAgName());

        CentralisedAgArch rec = masRunner.getAg(m.getReceiver());

        if (rec == null) {
            if (isRunning())
                throw new ReceiverNotFoundException("Receiver '" + m.getReceiver() + "' does not exist! Could not send " + m, m.getReceiver());
            else
                return;
        }
        rec.receiveMsg(m.clone()); // send a cloned message

        // notify listeners
        if (msgListeners != null)
            for (MsgListener l: msgListeners)
                l.msgSent(m);
    }

    public void receiveMsg(Message m) {
        mbox.offer(m);
        wakeUpSense();
    }

    public void broadcast(Message m) throws Exception {
        for (String agName: RuntimeServicesFactory.get().getAgentsNames()) {
            if (!agName.equals(this.getAgName())) {
                Message newm = m.clone();
                newm.setReceiver(agName);
                getFirstAgArch().sendMsg(newm);
            }
        }
    }

    // Default procedure for checking messages, move message from local mbox to C.mbox
    public void checkMail() {
        Circumstance C = getTS().getC();
        Message im = mbox.poll();
        while (im != null && getTS().resumeSense()) {
            C.addMsg(im);
            if (logger.isLoggable(Level.FINE)) logger.fine("received message: " + im);
            im = mbox.poll();
        }
    }

    public Collection<Message> getMBox() {
        return mbox;
    }

    /** called by the TS to ask the execution of an action in the environment */
    @Override
    public void act(ActionExec action) {
        //if (logger.isLoggable(Level.FINE)) logger.fine("doing: " + action.getActionTerm());

        if (isRunning()) {
            if (infraEnv != null) {
                infraEnv.act(getAgName(), action);
            } else {
                action.setResult(false);
                action.setFailureReason(new Atom("noenv"), "no environment configured!");
                actionExecuted(action);
            }
        }
    }

    public boolean canSleep() {
        return mbox.isEmpty() && isRunning();
    }

    private transient Object  syncMonitor = new Object();
    private volatile boolean inWaitSyncMonitor = false;

    /**
     * waits for a signal to continue the execution (used in synchronised
     * execution mode)
     */
    private void waitSyncSignal() {
        try {
            synchronized (syncMonitor) {
                inWaitSyncMonitor = true;
                syncMonitor.wait();
                inWaitSyncMonitor = false;
            }
        } catch (InterruptedException e) {
        } catch (Exception e) {
            logger.log(Level.WARNING,"Error waiting sync (1)", e);
        }
    }

    /**
     * inform this agent that it can continue, if it is in sync mode and
     * waiting a signal
     */
    public void receiveSyncSignal() {
        try {
            synchronized (syncMonitor) {
                while (!inWaitSyncMonitor && isRunning()) {
                    // waits the agent to enter in waitSyncSignal
                    syncMonitor.wait(50);
                }
                syncMonitor.notifyAll();
            }
        } catch (InterruptedException e) {
        } catch (Exception e) {
            logger.log(Level.WARNING,"Error waiting sync (2)", e);
        }
    }

    /**
     *  Informs the infrastructure tier controller that the agent
     *  has finished its reasoning cycle (used in sync mode).
     *
     *  <p><i>breakpoint</i> is true in case the agent selected one plan
     *  with the "breakpoint" annotation.
     */
    public void informCycleFinished(boolean breakpoint, int cycle) {
        infraControl.receiveFinishedCycle(getAgName(), breakpoint, cycle);
    }

    public RuntimeServices getRuntimeServices() {
        return RuntimeServicesFactory.get();
    }

    private RConf conf;

    private int cycles = 1;

    private int cyclesSense      = 1;
    private int cyclesDeliberate = 1;
    private int cyclesAct        = 1;

    public void setConf(RConf conf) {
        this.conf = conf;
    }

    public RConf getConf() {
        return conf;
    }

    public int getCycles() {
        return cycles;
    }

    public void setCycles(int cycles) {
        this.cycles = cycles;
    }

    public int getCyclesSense() {
        return cyclesSense;
    }

    public void setCyclesSense(int cyclesSense) {
        this.cyclesSense = cyclesSense;
    }

    public int getCyclesDeliberate() {
        return cyclesDeliberate;
    }

    public void setCyclesDeliberate(int cyclesDeliberate) {
        this.cyclesDeliberate = cyclesDeliberate;
    }

    public int getCyclesAct() {
        return cyclesAct;
    }

    public void setCyclesAct(int cyclesAct) {
        this.cyclesAct = cyclesAct;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = super.getStatus();

        status.put("cycle", getCycleNumber());
        status.put("idle", getTS().canSleep());

        // put intentions
        Circumstance c = getTS().getC();

        status.put("nbIntentions", c.getNbRunningIntentions() + c.getPendingIntentions().size());

        List<Map<String, Object>> ints = new ArrayList<>();
        Iterator<Intention> ii = c.getAllIntentions();
        while (ii.hasNext()) {
            Intention i = ii.next();
            Map<String, Object> iprops = new HashMap<>();
            iprops.put("id", i.getId());
            iprops.put("finished", i.isFinished());
            //iprops.put("suspended", i.isSuspended());
            iprops.put("state", i.getStateBasedOnPlace());
            if (i.isSuspended()) {
                iprops.put("suspendedReason", i.getSuspendedReason().toString());
            }
            iprops.put("size", i.size());
            ints.add(iprops);
        }
        status.put("intentions", ints);

        return status;
    }
}
