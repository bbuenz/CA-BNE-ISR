package ch.uzh.ifi.ce.cabne.verification;

import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;

import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.helpers.UtilityHelpers;
import ch.uzh.ifi.ce.cabne.pointwiseBR.Optimizer;
import ch.uzh.ifi.ce.cabne.strategy.VerificationStrategy1D;
import ch.uzh.ifi.ce.cabne.strategy.Strategy;

public class BoundingNewVerifier1D implements Verifier<Double, Double> {
	public interface winProbCalculator {
		public double calcWinProb(int i, Double bid, List<Strategy<Double, Double>> s);
	}
	
	BNESolverContext<Double, Double> context;
	winProbCalculator wpc;
	
	
	public BoundingNewVerifier1D(BNESolverContext<Double, Double> context, winProbCalculator wpc) {
		super();
		this.context = context;
		this.wpc = wpc;
	}
	
	public BoundingNewVerifier1D(BNESolverContext<Double, Double> context) {
		this(context, (i, bid, s) -> {
			// HUGE HACK: need to get winning probability, this is not easy with current API
			final double largeValue = 100000.;
			return context.integrator.computeExpectedUtility(i, largeValue, bid, s)/largeValue;
		});
	}
	
	

	public Strategy<Double, Double> convertStrategy(int gridsize, Strategy<Double, Double> s) {
		//return new VerificationStrategy1D(gridsize+1, 0.7, s);
		return new VerificationStrategy1D(gridsize+1, 1.0, s);
	}
	
	
	public double computeEpsilon(int gridsize, int i, Strategy<Double, Double> si, List<Strategy<Double, Double>> s) {
		// Compute epsilon bound using Theorem 1 from our IJCAI'17 paper
		double highestEpsilon = 0.0;
		double highestEpsilonOldBound = 0.0;
		double highestEpsilonEstimate = 0.0;
		Optimizer.Result<Double> oldresult = null;
		double oldb = 0, oldv=0;
		
		VerificationStrategy1D qsi = (VerificationStrategy1D) s.get(i);
		
		for (int j = 0; j<=gridsize; j++) {
			double v = qsi.getGridpoint(j);
			Double equilibriumBid = qsi.getBid(v);
			Optimizer.Result<Double> result = context.optimizer.findBR(i, v, equilibriumBid, s);

			// epsilon at control point itself
			//double epsilon = UtilityHelpers.absoluteLoss(result.oldutility, result.utility);
			highestEpsilonEstimate = Math.max(highestEpsilonEstimate, UtilityHelpers.absoluteLoss(result.oldutility, result.utility));
			
			
			
			// epsilon in interval between this and previous control point
			if (j!=0) {
				double winProb = wpc.calcWinProb(i, oldb, s);
				
				highestEpsilon = Math.max(highestEpsilon, Math.max(oldresult.utility - oldresult.oldutility, result.utility - oldresult.oldutility - winProb*(v - oldv)));
				highestEpsilonOldBound = Math.max(highestEpsilonOldBound, result.utility - oldresult.oldutility);

				// print out stuff

				System.out.format("v: %7.5f   bid: %7.5f   util: %11.8f   brutil: %11.8f   loss: %11.8f   Prob[win]: %6.4f\n", 
						oldv, oldb, oldresult.oldutility, oldresult.utility, oldresult.utility - oldresult.oldutility, winProb);
				System.out.format("v: %7.5f   bid: %7.5f   util: %11.8f   brutil: %11.8f   loss: %11.8f\n", 
						v, equilibriumBid, result.oldutility, result.utility, result.utility - result.oldutility);
				System.out.format("old bound: %11.8f   new bound: %11.8f\n\n", result.utility - oldresult.oldutility, Math.max(oldresult.utility - oldresult.oldutility, result.utility - oldresult.oldutility - winProb*(v - oldv)));
				
			}
			
			
			
			oldresult = result;
			oldv = v;
			oldb = equilibriumBid;
		}
		System.out.format("estimate: %11.8f   old bound: %11.8f   new bound: %11.8f\n\n", highestEpsilonEstimate, highestEpsilonOldBound, highestEpsilon);
		
		
		return highestEpsilon;
	}

}
