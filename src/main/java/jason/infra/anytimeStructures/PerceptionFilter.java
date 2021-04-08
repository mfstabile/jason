package jason.infra.anytimeStructures;

import jason.asSyntax.Literal;

public abstract class PerceptionFilter {

    public abstract boolean filter1(Literal perception);

    public boolean max(Literal perception) {
        return true;
    }

}
