package ch.uzh.ifi.ce.cabne.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.SortedMap;

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


public class LLGExtrinsicBNEpass1 extends LLGExperimentBase {
	
	
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
		context.activateConfig("innerloop");
		context.setMechanism(mechanism);
		context.setSampler(new AusubelBaranovLLGSampler(alpha, gamma, context));
		context.setIntegrator(new MCIntegrator<Double, Double>(context)); // note that this is not used when using statistical tests
		
		// read general algorithm settings from config
		int maxIters = Integer.parseInt(context.config.get("maxiters"));
		
		configureAlgorithm(algorithmName, context, seedFromIndex(runIndex));
		
		// create starting strategies
		ArrayList<Strategy<Double, Double>> strats = new ArrayList<>(3);
		strats.add(0, UnivariatePWLStrategy.makeTruthful(0.0, 1.0));
		strats.add(1, UnivariatePWLStrategy.makeTruthful(0.0, 1.0));
		strats.add(2, UnivariatePWLStrategy.makeTruthful(0.0, 2.0));
		
		// -----------------------------------------------------------------------------------
		// finished setup, now comes the main loop
		// -----------------------------------------------------------------------------------
		
		// String builder that assembles the output.
		StringBuilder builder = new StringBuilder();
		builder.append(String.format(" 0 INNER           0 1.000000  #  0.00000 0.00000 1.00000 1.00000\n"));

		long starttime = System.currentTimeMillis();
		for (int iteration=1; iteration <= maxIters; iteration++) {
			System.out.println("Starting Iteration " + iteration);
			context.advanceRngs();
			ArrayList<Strategy<Double, Double>> newstrats = new ArrayList<>(3);
			Result<Double, Double> result = context.brc.computeBR(0, strats);
			UnivariatePWLStrategy s = (UnivariatePWLStrategy) result.br;
			newstrats.add(0, s);
			newstrats.add(1, s);
			newstrats.add(2, strats.get(2)); // global player is always truthful
			
			// print out strategy
			long endtime = System.currentTimeMillis();
			builder.append(String.format("%2d INNER        %4d %f  # ", iteration, endtime - starttime, result.epsilonAbs));
			SortedMap<Double, Double> data = s.getData();
			for (double key : data.keySet()) {
				double value = data.get(key);
				builder.append(String.format(" %f %f", key, value));
			}
			builder.append("\n");
			
			// update strategy
			strats = newstrats;
			starttime = endtime;
			
		}
			
		// write out analytical BNE
		builder.append("-1 ANALYTICAL     -1 0.000000  # ");
		for (double v=0.0; v<=1.01; v += 0.01) {
			builder.append(String.format(" %f %f", v, analyticalBNE(v, mechanismName, alpha, gamma)));
		}

		Files.write(outputFile, builder.toString().getBytes(), 
				    StandardOpenOption.CREATE, 
				    StandardOpenOption.WRITE, 
				    StandardOpenOption.TRUNCATE_EXISTING);
	}
}
