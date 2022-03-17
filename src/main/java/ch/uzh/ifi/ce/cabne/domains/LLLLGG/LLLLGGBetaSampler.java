package ch.uzh.ifi.ce.cabne.domains.LLLLGG;

import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.domains.BidSampler;
import ch.uzh.ifi.ce.cabne.helpers.distributions.BetaEpsilonRealDist;
import ch.uzh.ifi.ce.cabne.strategy.Strategy;

import java.util.Iterator;
import java.util.List;

public class LLLLGGBetaSampler extends BidSampler<Double[], Double[]> {
    private final BetaEpsilonRealDist betaDist;
    private final double EPSILON = 1e-4;

    public LLLLGGBetaSampler(BNESolverContext<Double[], Double[]> context, double alpha, double beta) {
        super(context);
        betaDist = new BetaEpsilonRealDist(alpha, beta, EPSILON);
    }

    @Override
    public Double[][] sampleGame() {
        Double result[][] = new Double[6][];
        Iterator<double[]> rngiter = context.getRng(12).nextVectorIterator();
        double[] r = rngiter.next();

        for (int j = 0; j < 6; j++) {
            double maxValue = (j <= 3) ? 1.0 : 2.0;

            Double[] valueJ = new Double[]{
                    betaDist.inverseCumulativeProbability(r[2 * (j)]) * maxValue,
                    betaDist.inverseCumulativeProbability(r[2 * (j) + 1]) * maxValue
            };
            result[j] = valueJ;
        }

        return result;
    }


    public Iterator<Sample> conditionalBidIterator(int i, Double[] v, Double[] b, List<Strategy<Double[], Double[]>> s) {
        Iterator<double[]> rngiter = context.getRng(10).nextVectorIterator();
        // density is computed assuming uniform value distributions


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
                    double maxValue = (j <= 3) ? 1.0 : 2.0;

                    Double[] valueJ = new Double[]{
                            betaDist.inverseCumulativeProbability(r[2 * (j + offset)]) * maxValue,
                            betaDist.inverseCumulativeProbability(r[2 * (j + offset) + 1]) * maxValue
                    };
                    result[j] = s.get(j).getBid(valueJ);
                }

                return new Sample(1.0, result);
            }
        };
        return it;
    }
}
