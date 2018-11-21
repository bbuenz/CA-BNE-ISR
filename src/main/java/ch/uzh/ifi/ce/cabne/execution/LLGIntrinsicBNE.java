package ch.uzh.ifi.ce.cabne.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.SortedMap;

import ch.uzh.ifi.ce.cabne.BR.PWLBRCalculator;
import ch.uzh.ifi.ce.cabne.BR.AdaptivePWLBRCalculator;
import ch.uzh.ifi.ce.cabne.BR.BRCalculator.Result;
import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.domains.Mechanism;
import ch.uzh.ifi.ce.cabne.domains.LLG.AusubelBaranovLLGSampler;
import ch.uzh.ifi.ce.cabne.domains.LLG.NearestBid;
import ch.uzh.ifi.ce.cabne.domains.LLG.Proportional;
import ch.uzh.ifi.ce.cabne.domains.LLG.Proxy;
import ch.uzh.ifi.ce.cabne.domains.LLG.Quadratic;
import ch.uzh.ifi.ce.cabne.integration.MCIntegrator;
import ch.uzh.ifi.ce.cabne.strategy.Strategy;
import ch.uzh.ifi.ce.cabne.strategy.UnivariatePWLStrategy;


public class LLGIntrinsicBNE extends LLGExperimentBase {
	
	
	public static void main(String[] args) throws InterruptedException, IOException {
		// Extract arguments.
		// this entry point is called with the name of the rule, alpha, gamma, etc.
		// It is meant to be called directly. Another entrypoint called SbatchEntryPoint can be called with
		// a single int as argument.
		String mechanismName = args[0];
		double alpha = Double.parseDouble(args[1]);
		double gamma = Double.parseDouble(args[2]);
		String algorithmName = args[3];
		int runIndex = Integer.parseInt(args[4]);
		String configfile = args[5];
		Path outputfolder = Paths.get(args[6]);
		String experimentId = args[7]; // can be 2, 4b or 4c
		
		Path outputFile = outputfolder.resolve(String.format("%s-%2.1f-%2.1f-%s-run%03d", mechanismName, alpha, gamma, algorithmName, runIndex));

		Mechanism<Double, Double> mechanism;
		switch (mechanismName) {
			case "proportional":
				mechanism = new Proportional();
				break;
			case "quadratic":
				mechanism = new Quadratic();
				break;
			case "proxy":
				mechanism = new Proxy();
				break;
			case "nearestbid":
				mechanism = new NearestBid();
				break;
			default:
				throw new RuntimeException("Unknown rule");
		}
		
		// create context
		BNESolverContext<Double, Double> context = new BNESolverContext<>();
		context.parseConfig(configfile);
		context.setMechanism(mechanism);
		context.setSampler(new AusubelBaranovLLGSampler(alpha, gamma, context));
		context.setIntegrator(new MCIntegrator<Double, Double>(context)); // note that this is not used when using statistical tests
		
		// read general algorithm settings from config
		int maxIters = Integer.parseInt(context.config.get("maxiters"));
		double targetEpsilon = Double.parseDouble(context.config.get("epsilon"));
		
		switch (experimentId) {
		case "2":
			// make parallel runs with different random seeds
			configureAlgorithm("6adaptive", context, seedFromIndex(runIndex));
		case "4b":
			// make parallel runs with different random seeds
			configureAlgorithm("6adaptive", context, seedFromIndex(runIndex));
			break;
		case "4c":
			// make runs with same random seed
			configureAlgorithm("6adaptive", context, 0);
			break;
		default:
			throw new RuntimeException("Unknown experiment");
		}
		
		// create starting strategies
		ArrayList<Strategy<Double, Double>> strats = new ArrayList<>(3);
		strats.add(0, UnivariatePWLStrategy.makeTruthful(0.0, 1.0));
		strats.add(1, UnivariatePWLStrategy.makeTruthful(0.0, 1.0));
		strats.add(2, UnivariatePWLStrategy.makeTruthful(0.0, 2.0));
		
		// -----------------------------------------------------------------------------------
		// finished setup, now comes the main loop
		// -----------------------------------------------------------------------------------
		
		int iteration = 1;
		
		// String builder that assembles the output.
		StringBuilder builder = new StringBuilder();
		builder.append(String.format(" 0 INNER           0 1.000000  #  0.00000 0.00000 1.00000 1.00000\n"));

		
		long endtime = System.currentTimeMillis();
		long starttime;
		for (; iteration <= maxIters; iteration++) {
			// This is the outer loop. First thing we do is go into the inner loop
			
			for (;iteration <= maxIters; iteration++) {
				// This is the inner loop

				starttime = endtime;
				//System.out.println("Starting Inner Iteration " + iteration);
				context.advanceRngs();
				context.activateConfig("innerloop");
				context.activateConfig("common");
				context.setBRC(new AdaptivePWLBRCalculator(context));
				
				ArrayList<Strategy<Double, Double>> newstrats = new ArrayList<>(3);
				Result<Double, Double> result = context.brc.computeBR(0, strats);
				UnivariatePWLStrategy s = (UnivariatePWLStrategy) result.br;
				newstrats.add(0, s);
				newstrats.add(1, s);
				newstrats.add(2, strats.get(2));
				
				// print out strategy
				endtime = System.currentTimeMillis();
				builder.append(String.format("%2d INNER        %4d %f  # ", iteration, endtime - starttime, result.epsilonAbs));
				SortedMap<Double, Double> data = s.getData();
				for (double key : data.keySet()) {
					double value = data.get(key);
					builder.append(String.format(" %f %f", key, value));
				}
				builder.append("\n");
				
				// update strategy
				strats = newstrats;
				
				// if we are converged according to the eps on the control points where we computed best responses, then
				// break out to the outer loop

				if (algorithmName.equals("3adaptive")) {
					if (result.epsilonAbs <= 0.8*targetEpsilon) break;
				} else if (algorithmName.equals("2naiveeveryfive")) {
					if (iteration % 5 == 4) break;
				} else if (algorithmName.equals("1naive")) {
					break;
				} else {
					throw new RuntimeException("unknown breaking condition");
				}
			}
			
			iteration++;
			starttime = endtime;
			//System.out.println("Starting Outer Iteration " + iteration);
			context.advanceRngs();
			context.activateConfig("outerloop");
			context.activateConfig("common");
			context.setBRC(new PWLBRCalculator(context));
			
			ArrayList<Strategy<Double, Double>> newstrats = new ArrayList<>(3);
			Result<Double, Double> result = context.brc.computeBR(0, strats);
			UnivariatePWLStrategy s = (UnivariatePWLStrategy) result.br;
			newstrats.add(0, s);
			newstrats.add(1, s);
			newstrats.add(2, strats.get(2));
			
			// print out strategy
			endtime = System.currentTimeMillis();
			builder.append(String.format("%2d OUTER        %4d %f  # ", iteration, endtime - starttime, result.epsilonAbs));
			SortedMap<Double, Double> data = s.getData();
			for (double key : data.keySet()) {
				double value = data.get(key);
				builder.append(String.format(" %f %f", key, value));
			}
			builder.append("\n");
			
			// update strategy
			strats = newstrats;

			if (result.epsilonAbs <= targetEpsilon) {
				break;
			}
		}
			
		// depending on which experiment we run, need to reset seed at verification step
		switch (experimentId) {
		case "2":
			// don't need to do anything, just continue using the same stream of random numbers
			break;
		case "4b":
			// runs had different seeds, now verify them with the same seed.
			configureAlgorithm("6adaptive", context, 0);
			break;
		case "4c":
			// runs had the same seed, now verify them with different seeds.
			configureAlgorithm("6adaptive", context, seedFromIndex(runIndex));
			break;
		default:
			throw new RuntimeException("Unknown experiment");
		}
		

		starttime = endtime;
		//System.out.println("Starting Verification Step");
		context.advanceRngs();
		context.activateConfig("verificationstep");
		context.activateConfig("common");
		context.setBRC(new PWLBRCalculator(context)); // redundant, since it should already be set in the outer loop
		
		// compute estimated epsilon (not formal upper bound)
		Result<Double, Double> result = context.brc.computeBR(0, strats);
		UnivariatePWLStrategy s = (UnivariatePWLStrategy) result.br;
		

		// print out strategy
		endtime = System.currentTimeMillis();
		builder.append(String.format("%2d VERIFICATION %4d %11.9f  # ", iteration + 1, endtime - starttime, result.epsilonAbs));
		SortedMap<Double, Double> data = s.getData();
		for (double key : data.keySet()) {
			double value = data.get(key);
			builder.append(String.format(" %f %f", key, value));
		}
		builder.append("\n");

		// write out analytical BNE
		builder.append("-1 ANALYTICAL     -1 0.000000000  # ");
		for (double v=0.0; v<=1.01; v += 0.01) {
			builder.append(String.format(" %f %f", v, analyticalBNE(v, mechanismName, alpha, gamma)));
		}
		

		Files.write(
			outputFile, builder.toString().getBytes(), 
			StandardOpenOption.CREATE, 
			StandardOpenOption.WRITE, 
			StandardOpenOption.TRUNCATE_EXISTING
		);
		
	}
}
