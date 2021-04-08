import java.util.LinkedList;
import java.util.Random;
import jason.infra.anytimeStructures.PerceptionFilter;
import jason.asSyntax.Literal;

public class CustomPerceptionFilter extends PerceptionFilter{

    @Override
    public boolean filter1(Literal perception) {
        return true;
    }

    public boolean filter2(Literal perception) {
        return true;
    }


}
