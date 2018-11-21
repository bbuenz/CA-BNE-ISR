package ch.uzh.ifi.ce.cabne.execution.modifiedclasses;

import java.util.List;
import java.util.TreeMap;

import ch.uzh.ifi.ce.cabne.BR.BRCalculator;
import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.helpers.UtilityHelpers;
import ch.uzh.ifi.ce.cabne.pointwiseBR.Optimizer;
import ch.uzh.ifi.ce.cabne.strategy.Strategy;
import ch.uzh.ifi.ce.cabne.strategy.UnivariatePWLStrategy;

public class HackyPWLBRCalculator implements BRCalculator<Double, Double> {
	BNESolverContext<Double, Double> context;
	

	public HackyPWLBRCalculator(BNESolverContext<Double, Double> context) {
		this.context = context;
	}


	public Result<Double, Double> computeBR(int i, List<Strategy<Double, Double>> s) {
		int nPoints = Integer.parseInt(context.config.get("gridsize"));
		
		TreeMap<Double, Double> pointwiseBRs = new TreeMap<>();
		double epsilonAbs = 0.0;
		double epsilonRel = 0.0;

		double maxValue = s.get(i).getMaxValue();
		
		for (int j = 0; j<=nPoints; j++) {
			double v = maxValue * ((double) j) / (nPoints);
			Double oldbid = s.get(i).getBid(v);
			((HackyRandomGenerator) context.getRng(2)).resetIndex();
			((HackyRandomGenerator) context.getRng(1)).resetIndex();
			Optimizer.Result<Double> result = context.optimizer.findBR(i, v, oldbid, s);
			epsilonAbs = Math.max(epsilonAbs, UtilityHelpers.absoluteLoss(result.oldutility, result.utility));
			epsilonRel = Math.max(epsilonRel, UtilityHelpers.relativeLoss(result.oldutility, result.utility));
						
			Double newbid = context.updateRule.update(v, oldbid, result.bid, result.oldutility, result.utility);
			pointwiseBRs.put(v,  newbid);			
		}
		
		return new Result<Double, Double>(new UnivariatePWLStrategy(pointwiseBRs), epsilonAbs, epsilonRel);
	}

}
