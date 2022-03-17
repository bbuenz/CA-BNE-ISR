package ch.uzh.ifi.ce.cabne.execution;

import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.domains.BidSampler;
import ch.uzh.ifi.ce.cabne.domains.LLLLGG.*;
import ch.uzh.ifi.ce.cabne.helpers.SampleCollector;
import ch.uzh.ifi.ce.cabne.randomsampling.NaiveRandomGenerator;
import ch.uzh.ifi.ce.cabne.strategy.GridStrategy2D;
import com.opencsv.CSVWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

public class LLLLGGEvaluation {

    public static void main(String[] args) throws IOException {
        Path bneFile = Paths.get(args[0]);
        LLLLGGStrategyParser parser = new LLLLGGStrategyParser();
        List<GridStrategy2D> strategies = parser.parse(bneFile);
        BNESolverContext<Double[], Double[]> context = new BNESolverContext<>();
        context.parseConfig(args[1]);
        LLLLGGMechanism mechanism = LLLLGGBestResponse.getMechanism(context);
        System.out.println(strategies.size());
        int iterations = 1000000;
        try {
            iterations = Integer.parseInt(args[2]);
        } catch (Exception ignored) {

        }
        double[] evaluation = evaluate(strategies, mechanism, context, iterations);
        if (args.length > 3) {
            Path csvFile = Paths.get(args[3]);
            CSVWriter writer;
            if (Files.exists(csvFile)) {

                writer = new CSVWriter(Files.newBufferedWriter(csvFile, StandardOpenOption.APPEND, StandardOpenOption.WRITE));
            } else {
                try {
                    Files.createDirectories(csvFile.getParent());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                writer = new CSVWriter(Files.newBufferedWriter(csvFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE));
                writer.writeNext(new String[]{"Rule", "Trials", "Efficiency", "Incentives", "Revenue", "Efficiency Stderr","Incentive Stderr", "Revenue Stderr", });
            }
            System.out.println(bneFile.getParent());
            String[] csvString = new String[]{bneFile.getParent().getFileName().toString(), String.valueOf(iterations), String.valueOf(evaluation[0]), String.valueOf(evaluation[1]), String.valueOf(evaluation[2]), String.valueOf(evaluation[3]), String.valueOf(evaluation[4]), String.valueOf(evaluation[5])};
            writer.writeNext(csvString);

            writer.close();
        } else {
            System.out.println(Arrays.toString(evaluation));
        }
    }

    public static double[] evaluate(List<GridStrategy2D> strategies, LLLLGGMechanism mechanism, BNESolverContext<Double[], Double[]> context, int iterations) {
        LLLLGGWD wd = new LLLLGGWD();
        context.setRng(12, new NaiveRandomGenerator(12));
        BidSampler<Double[], Double[]> sampler;
        if (context.hasParameter("sampler.betadist")) {
            double alpha = context.getDoubleParameter("sampler.betadist.alpha");
            double beta = context.getDoubleParameter("sampler.betadist.beta");
            if (context.hasParameter("sampler.betadist.globaluniform")) {
                sampler = new LLLLGGGlobalUniformBetaSampler(context, alpha, beta);

            } else {
                sampler = new LLLLGGBetaSampler(context, alpha, beta);
            }
        } else {
            sampler = new LLLLGGSampler(context);
        }
        SampleCollector efficiencyCollector = new SampleCollector(2);
        SampleCollector revenueCollector = new SampleCollector(2);

        for (int k = 0; k < iterations; ++k) {
            Double[][] bids = new Double[6][];
            Double[][] game = sampler.sampleGame();
            for (int i = 0; i < 6; ++i) {
                bids[i] = strategies.get(i).getBid(game[i]);

            }

            List<int[]> vcgalloc = wd.solveWD(game);
            List<int[]> ccgalloc = wd.solveWD(bids);

            double truthfulValue = wd.valueOfAllocation(vcgalloc.get(0), game);

            double bneValue = wd.valueOfAllocation(ccgalloc.get(0), game);
            double[] vcgPrices = wd.computeVCG(game, vcgalloc.get(0));
            double[] ccgPrices = mechanism.computePayments(bids, ccgalloc.get(0));
            double vcgGameRevenue = Arrays.stream(vcgPrices).sum();
            double ccgGameRevenue = Arrays.stream(ccgPrices).sum();
            revenueCollector.addSample(ccgGameRevenue, vcgGameRevenue);
            efficiencyCollector.addSample(bneValue, truthfulValue);

        }

        double[] efficiencyMeanError = efficiencyCollector.computeFractionalMeanError(0, 1);
        double[] revenueMeanError = revenueCollector.computeFractionalMeanError(0, 1);

        int numRuns = 40;
        double[] results = new double[numRuns];
        for (int j = 0; j < numRuns; ++j) {
            int perRunSamples = iterations / 10;
            double[] playerIncentives = new double[6];
            for (int k = 0; k < perRunSamples; ++k) {
                Double[][] bids = new Double[6][];
                Double[][] game = sampler.sampleGame();
                for (int i = 0; i < 6; ++i) {


                    bids[i] = strategies.get(i).getBid(game[i]);
                    double incentiveI = Math.pow(bids[i][0] - game[i][0], 2) + Math.pow(bids[i][1] - game[i][1], 2);
                    playerIncentives[i] += incentiveI;

                }

            }
            double newIncentives = 0;
            for (int i = 0; i < playerIncentives.length; ++i) {
                newIncentives += Math.sqrt(playerIncentives[i] / (perRunSamples));
            }
            results[j] = newIncentives;

        }
        Arrays.sort(results);
        double incentiveMean = Arrays.stream(results).sum() / numRuns;
        double standardError = results[(int) Math.round(numRuns * 3.0 / 4.0)] - results[(int) Math.round(numRuns / 4.0)];


        return new double[]{efficiencyMeanError[0],incentiveMean, revenueMeanError[0], efficiencyMeanError[1],standardError, revenueMeanError[1]};

    }
}
