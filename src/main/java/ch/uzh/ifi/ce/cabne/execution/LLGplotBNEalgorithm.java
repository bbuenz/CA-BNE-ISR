package ch.uzh.ifi.ce.cabne.execution;

import java.io.IOException;
import java.util.Map;

import ch.uzh.ifi.ce.cabne.BR.AdaptivePWLBRCalculator;
import ch.uzh.ifi.ce.cabne.BR.PWLBRCalculator;
import ch.uzh.ifi.ce.cabne.algorithm.BNEAlgorithm;
import ch.uzh.ifi.ce.cabne.algorithm.BNEAlgorithmCallback;
import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.domains.LLG.AusubelBaranovLLGSampler;
import ch.uzh.ifi.ce.cabne.domains.LLG.LLGSampler;
import ch.uzh.ifi.ce.cabne.domains.LLG.Proxy;
import ch.uzh.ifi.ce.cabne.domains.LLG.Quadratic;
import ch.uzh.ifi.ce.cabne.integration.MCIntegrator;
import ch.uzh.ifi.ce.cabne.pointwiseBR.PatternSearch;
import ch.uzh.ifi.ce.cabne.pointwiseBR.UnivariatePattern;
import ch.uzh.ifi.ce.cabne.pointwiseBR.updateRule.UnivariateDampenedUpdateRule;
import ch.uzh.ifi.ce.cabne.pointwiseBR.updateRule.UnivariateLinearUpdateRule;
import ch.uzh.ifi.ce.cabne.pointwiseBR.updateRule.UpdateRule;
import ch.uzh.ifi.ce.cabne.randomsampling.CommonRandomGenerator;
import ch.uzh.ifi.ce.cabne.strategy.UnivariatePWLStrategy;
import ch.uzh.ifi.ce.cabne.verification.BoundingVerifier1D;
import ch.uzh.ifi.ce.cabne.verification.EstimatingVerifier1D;


public class LLGplotBNEalgorithm {
	
	public static void main(String[] args) throws InterruptedException, IOException {		

		// create context and read config
		BNESolverContext<Double, Double> context = new BNESolverContext<>();
		String configfile = args[0];
		context.parseConfig(configfile);
		
		// initialize all algorithm pieces
		context.setOptimizer(new PatternSearch<>(context, new UnivariatePattern()));
		context.setIntegrator(new MCIntegrator<>(context));
		context.setRng(1, new CommonRandomGenerator(1));
		context.setRng(2, new CommonRandomGenerator(2));
		context.setUpdateRule(new UnivariateDampenedUpdateRule(0.2, 0.7, 0.5 / context.getDoubleParameter("epsilon"), true));
		context.setBRC(new PWLBRCalculator(context));
		context.setOuterBRC(new PWLBRCalculator(context));
		//context.setVerifier(new EstimatingVerifier1D(context));
		context.setVerifier(null);
		
		// instanciate auction setting
		context.setMechanism(new Proxy());
		context.setSampler(new AusubelBaranovLLGSampler(1.0, 0.5, context));
		
		BNEAlgorithm<Double, Double> bneAlgo = new BNEAlgorithm<>(3, context);
		
		// add bidders, giving each an initial strategy and telling the algorithm which ones to update.
		// bidder 0 (first local bidder) does a best response in each iteration
		// bidder 1 (second local bidder) plays symmetrically to bidder 0
		// bidder 2 (global bidder) plays truthful and thus doesn't update his strategy.
		bneAlgo.setInitialStrategy(0, UnivariatePWLStrategy.makeTruthful(0.0, 1.0));
		bneAlgo.setInitialStrategy(2, UnivariatePWLStrategy.makeTruthful(0.0, 2.0));
		bneAlgo.makeBidderSymmetric(1, 0);
		bneAlgo.makeBidderNonUpdating(2);
		
		// create callback that prints out first local player's strategy after each iteration
		BNEAlgorithmCallback<Double, Double> callback = (iteration, type, strategies, epsilon) -> {
			
			// HACK: start with a smooth algo, then adjust to using raw BR to make a pretty graph
			if (iteration==2) context.setUpdateRule(new UnivariateLinearUpdateRule(1.0));
			
			// print out strategy
			StringBuilder builder = new StringBuilder();
			//builder.append(String.format("%2d", iteration));
			//builder.append(String.format(" %7.6f  ", epsilon));
			builder.append(String.format("%2d %s        %4d %f  # ", iteration, type.name(), 0, epsilon));
			
			// cast s to UnivariatePWLStrategy to get access to underlying data structure.
			UnivariatePWLStrategy sPWL = (UnivariatePWLStrategy) strategies.get(0);
			for (Map.Entry<Double, Double> e : sPWL.getData().entrySet()) {
				builder.append(String.format("%7.6f",e.getKey()));
				builder.append(" ");
				builder.append(String.format("%7.6f",e.getValue()));
				builder.append("  ");
			}
			
			// alternatively, just sample the strategy on a regular grid.
			/*
			for (int i=0; i<=100; i++) {
				double v = s.getMaxValue() * i / ((double) gridSize);
				builder.append(String.format("%7.6f",v));
				builder.append(" ");
				builder.append(String.format("%7.6f",s.getBid(v)));
				builder.append("  ");
			}
			*/
			System.out.println(builder.toString());
		};
		bneAlgo.setCallback(callback);
		
		BNEAlgorithm.Result<Double, Double> result;
		result = bneAlgo.run();
		//System.out.format("Algorithm finished with eps=%f", result.epsilon);
    }
}
