package graphql.analysis;

import graphql.masker.PublicApi;
import graphql.analysis.instrumentation.MaxQueryComplexityInstrumentation;

/**
 * Used to calculate the complexity of a field. Used by {@link MaxQueryComplexityInstrumentation}.
 */
@PublicApi
@FunctionalInterface
public interface FieldComplexityCalculator {

    /**
     * Calculates the complexity of a field
     *
     * @param environment     several information about the current field
     * @param childComplexity the sum of all child complexity scores
     *
     * @return the calculated complexity
     */
    int calculate(FieldComplexityEnvironment environment, int childComplexity);

}
