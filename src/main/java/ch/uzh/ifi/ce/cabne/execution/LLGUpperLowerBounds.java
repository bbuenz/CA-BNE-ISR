package ch.uzh.ifi.ce.cabne.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.SortedMap;

import ch.uzh.ifi.ce.cabne.algorithm.BNEAlgorithm;
import ch.uzh.ifi.ce.cabne.algorithm.BNEAlgorithmCallback;
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
import ch.uzh.ifi.ce.cabne.verification.BoundingQuadraticGridVerifier1D;
import ch.uzh.ifi.ce.cabne.verification.EstimatingVerifier1D;


public class LLGUpperLowerBounds extends LLGExperimentBase {
	
	
	public static void main(String[] args) throws InterruptedException, IOException {
		// Extract arguments.
		// this entry point is called with the name of the rule, alpha, etc.
		// It is meant to be called directly. Another entrypoint called SbatchEntryPoint can be called with
		// a single int as argument.
		String mechanismName = args[0];
		double alpha = Double.parseDouble(args[1]);
		int controlpointmodifier = Integer.parseInt(args[2]);
		String verifier = args[3];
		String configfile = args[4];
		Path outputfolder = Paths.get(args[5]);
		
		Path outputFile = outputfolder.resolve(String.format("%s-%2.1f-%s-%06d", mechanismName, alpha, verifier, controlpointmodifier));

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
		context.setSampler(new AusubelBaranovLLGSampler(alpha, 0.0, context));
		context.setIntegrator(new MCIntegrator<Double, Double>(context)); // note that this is not used when using statistical tests
				
		
		// configure algorithm
		configureAlgorithm("6adaptive", context, 0);
		switch (verifier) {
		case "Exact":
			//context.setVerifier(new ExactUnivariateVerifier(context));
			context.setVerifier(new BoundingQuadraticGridVerifier1D(context));
			break;
		case "Heuristic":
			context.setVerifier(new EstimatingVerifier1D(context));
			break;
		default:
			throw new RuntimeException();
		}
		
		// change number of CPs
		// TODO: there should be a cleaner way to edit individual configuration parameters
		int gridsize = context.getIntParameter("verificationstep.gridsize");
		context.config.put("verificationstep.gridsize", Integer.toString(gridsize * controlpointmodifier));
		System.out.println(context.getIntParameter("verificationstep.gridsize"));
		
		BNEAlgorithm<Double, Double> bneAlgo = new BNEAlgorithm<>(3, context);
		
		// add bidders
		bneAlgo.setInitialStrategy(0, UnivariatePWLStrategy.makeTruthful(0.0, 1.0));
		bneAlgo.setInitialStrategy(2, UnivariatePWLStrategy.makeTruthful(0.0, 2.0));
		bneAlgo.makeBidderSymmetric(1, 0);
		bneAlgo.makeBidderNonUpdating(2);
		
		// create callback that prints out first local player's strategy after each iteration

		// String builder that assembles the output.
		StringBuilder builder = new StringBuilder();
		builder.append(String.format(" 0 INNER           0 1.000000  #  0.00000 0.00000 1.00000 1.00000\n"));
		
		BNEAlgorithmCallback<Double, Double> callback = new BNEAlgorithmCallback<Double, Double>() {
			@Override
			public void afterIteration(int iteration, BNEAlgorithm.IterationType type, List<Strategy<Double, Double>> strategies, double epsilon) {
				
				String template;
				switch (type) {
				case INNER:
					template = "%2d INNER         000 %f  # ";
					break;
				case OUTER:
					template = "%2d OUTER         000 %f  # ";
					break;
				case VERIFICATION:
					template = "%2d VERIFICATION  000 %f  # ";
					break;
				default:
					throw new RuntimeException();
				}
				
				UnivariatePWLStrategy s = (UnivariatePWLStrategy) strategies.get(0);
				builder.append(String.format(template, iteration, epsilon));
				if (type == BNEAlgorithm.IterationType.VERIFICATION) {
					builder.append("\n");
					return;
				}
				SortedMap<Double, Double> data = s.getData();
				for (double key : data.keySet()) {
					double value = data.get(key);
					builder.append(String.format(" %f %f", key, value));
				}
				builder.append("\n");
				
			}
		};
		bneAlgo.setCallback(callback);
		
		bneAlgo.run();
		

		Files.write(
			outputFile, builder.toString().getBytes(), 
			StandardOpenOption.CREATE, 
			StandardOpenOption.WRITE, 
			StandardOpenOption.TRUNCATE_EXISTING
		);
		
	}
}
