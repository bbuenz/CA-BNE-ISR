package ch.uzh.ifi.ce.cabne.verification;

import java.util.List;

import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.helpers.UtilityHelpers;
import ch.uzh.ifi.ce.cabne.pointwiseBR.Optimizer;
import ch.uzh.ifi.ce.cabne.strategy.VerificationStrategy1D;
import ch.uzh.ifi.ce.cabne.strategy.Strategy;

public class BoundingQuadraticGridVerifier1D implements Verifier<Double, Double> {
	
	BNESolverContext<Double, Double> context;

	public BoundingQuadraticGridVerifier1D(BNESolverContext<Double, Double> context) {
		super();
		this.context = context;
	}

	public Strategy<Double, Double> convertStrategy(int gridsize, Strategy<Double, Double> s) {
		return new VerificationStrategy1D(gridsize+1, 0.7, s);
	}
	
	
	public double computeEpsilon(int gridsize, int i, Strategy<Double, Double> si, List<Strategy<Double, Double>> s) {
		// Compute epsilon bound using Theorem 1 from our IJCAI'17 paper
		double highestEpsilon = 0.0;
		Optimizer.Result<Double> oldresult = null;
		
		VerificationStrategy1D qsi = (VerificationStrategy1D) s.get(i);
		
		for (int j = 0; j<=gridsize; j++) {
			double v = qsi.getGridpoint(j);
			Double equilibriumBid = qsi.getBid(v);
			Optimizer.Result<Double> result = context.optimizer.findBR(i, v, equilibriumBid, s);

			// epsilon at control point itself
			double epsilon = UtilityHelpers.absoluteLoss(result.oldutility, result.utility);
			highestEpsilon = Math.max(highestEpsilon, epsilon);
			
			// epsilon in interval between this and previous control point
			if (j!=0) {
				highestEpsilon = Math.max(highestEpsilon, result.utility - oldresult.utility + epsilon);
			}
			oldresult = result;
		}
		return highestEpsilon;
	}

}
