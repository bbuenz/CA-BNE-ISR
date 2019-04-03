package ch.uzh.ifi.ce.cabne.execution;

import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.domains.LLLLGG.LLLLGGMechanism;
import ch.uzh.ifi.ce.cabne.domains.LLLLGG.LLLLGGParametrizedCoreSelectingRule;
import ch.uzh.ifi.ce.cabne.domains.LLLLGG.LLLLGGStrategyParser;
import ch.uzh.ifi.ce.cabne.domains.LLLLGG.LLLLGGWD;
import ch.uzh.ifi.ce.cabne.strategy.GridStrategy2D;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class LLLLGGEvaluation {

    public static void main(String[] args) throws IOException {
        Path bneFile = Paths.get(args[0]);
        LLLLGGStrategyParser parser = new LLLLGGStrategyParser();
        List<GridStrategy2D> strategies = parser.parse(bneFile);
        BNESolverContext<Double[], Double[]> context = new BNESolverContext<>();
        context.parseConfig(args[1]);
        LLLLGGMechanism mechanism=LLLLGGBestResponse.getMechanism(context);
        System.out.println(strategies.size());
        int iterations=100000;
        try{
            iterations=Integer.parseInt(args[2]);
        }catch (Exception ignored) {

        }
        evaluate(strategies, mechanism,iterations);

    }
    public static double[] evaluate(List<GridStrategy2D> strategies, LLLLGGMechanism mechanism, int iterations) {
        LLLLGGWD wd = new LLLLGGWD();
        Random rng = new Random();
        double totalvcg = 0;
        double totalccg = 0;
        double revenue = 0;
        double vcgRevenue = 0;
        double incentives=0;
        for (int k = 0; k < iterations; ++k) {
            double gameDistance=0;
            Double[][] bids = new Double[6][];
            Double[][] game = new Double[6][];
            for (int i = 0; i < 6; ++i) {
                if (i < 4) {
                    game[i] = new Double[]{
                            rng.nextDouble(),
                            rng.nextDouble()
                    };
                } else {
                    game[i] = new Double[]{
                            rng.nextDouble() * 2,
                            rng.nextDouble() * 2
                    };
                }

                bids[i] = strategies.get(i).getBid(game[i]);
                gameDistance+=Math.pow(bids[i][0]-game[i][0],2);
                gameDistance+=Math.pow(bids[i][1]-game[i][1],2);

            }
            incentives+=Math.sqrt(gameDistance);
            List<int[]> vcgalloc = wd.solveWD(game);
            List<int[]> ccgalloc = wd.solveWD(bids);

            totalvcg += wd.valueOfAllocation(vcgalloc.get(0), game);

            totalccg += wd.valueOfAllocation(ccgalloc.get(0), game);
            double[] vcgPrices = wd.computeVCG(game, vcgalloc.get(0));
            double[] ccgPrices = mechanism.computePayments(bids, ccgalloc.get(0));
            revenue += Arrays.stream(vcgPrices).sum();
            vcgRevenue += Arrays.stream(ccgPrices).sum();
        }
        System.out.println(totalccg / totalvcg);
        System.out.println(vcgRevenue);
        System.out.println(vcgRevenue/revenue);
        System.out.println(incentives/iterations);
        return null;

    }
}
