package ch.uzh.ifi.ce.cabne.domains.LLLLGG;

import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.domains.BidSampler;
import ch.uzh.ifi.ce.cabne.helpers.distributions.BetaEpsilonRealDist;
import ch.uzh.ifi.ce.cabne.strategy.Strategy;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Iterator;
import java.util.List;

public class LLLLGGGlobalUniformBetaSampler extends BidSampler<Double[], Double[]> {
    private final BetaEpsilonRealDist betaDist;
    private final double EPSILON = 1e-4;

    public LLLLGGGlobalUniformBetaSampler(BNESolverContext<Double[], Double[]> context, double alpha, double beta) {
        super(context);
        betaDist = new BetaEpsilonRealDist(alpha, beta, EPSILON);
    }

    public Double[][] sampleGame() {
        double[] r = context.getRng(12).nextVectorIterator().next();
        Double[][] game = new Double[6][];
        for (int j = 0; j < 6; j++) {
            Double[] valueJ;
            if (j <= 3) {
                valueJ = new Double[]{
                        betaDist.inverseCumulativeProbability(r[2 * (j)]),
                        betaDist.inverseCumulativeProbability(r[2 * (j) + 1])
                };
            } else {
                valueJ = new Double[]{
                        r[2 * (j)] * 2.0,
                        r[2 * (j) + 1] * 2.0
                };
            }

            game[j] = valueJ;

        }
        return game;
    }

    public Iterator<Sample> conditionalBidIterator(int i, Double[] v, Double[] b, List<Strategy<Double[], Double[]>> s) {
        Iterator<double[]> rngiter = context.getRng(10).nextVectorIterator();
        // density is computed assuming uniform value distributions
        double densityTmp = 1.0;


        Iterator<Sample> it = new Iterator<Sample>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Sample next() {
                double[] r;
                Double result[][] = new Double[6][];

                result[i] = b;

                r = rngiter.next();
                for (int j = 0; j < 6; j++) {
                    if (j == i) continue;
                    int offset = (j > i) ? -1 : 0;
                    Double[] valueJ;
                    if (j <= 3) {
                        valueJ = new Double[]{
                                betaDist.inverseCumulativeProbability(r[2 * (j + offset)]),
                                betaDist.inverseCumulativeProbability(r[2 * (j + offset) + 1])
                        };
                    } else {
                        valueJ = new Double[]{
                                r[2 * (j + offset)] * 2.0,
                                r[2 * (j + offset) + 1] * 2.0
                        };
                    }

                    result[j] = s.get(j).getBid(valueJ);

                }

                return new Sample(1.0, result);
            }
        };
        return it;
    }
}
