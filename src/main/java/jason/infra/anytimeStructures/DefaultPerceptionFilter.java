package jason.infra.anytimeStructures;

import jason.asSyntax.Literal;

public class DefaultPerceptionFilter extends PerceptionFilter {
    @Override
    public boolean filter1(Literal perception) {
        return true;
    }
}
