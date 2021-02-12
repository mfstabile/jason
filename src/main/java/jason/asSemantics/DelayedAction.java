package jason.asSemantics;

public class DelayedAction implements Comparable<DelayedAction>{
    private ActionExec ac;
    private Intention i;

    public DelayedAction(ActionExec ac, Intention i) {
        this.ac = ac;
        this.i = i;
    }

    @Override
    public int compareTo(DelayedAction o) {
        return ac.compareTo(o.ac);
    }

    public ActionExec getAction() {
        return ac;
    }

    public void setAction(ActionExec ac) {
        this.ac = ac;
    }

    public Intention getIntention() {
        return i;
    }

    public void setIntention(Intention i) {
        this.i = i;
    }
}
