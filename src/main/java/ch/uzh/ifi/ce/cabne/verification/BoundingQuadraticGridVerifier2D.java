package ch.uzh.ifi.ce.cabne.verification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.helpers.UtilityHelpers;
import ch.uzh.ifi.ce.cabne.pointwiseBR.Optimizer;
import ch.uzh.ifi.ce.cabne.strategy.VerificationStrategy;
import ch.uzh.ifi.ce.cabne.strategy.Strategy;

public class BoundingQuadraticGridVerifier2D implements Verifier<Double[], Double[]> {
	BNESolverContext<Double[], Double[]> context;
	

	public BoundingQuadraticGridVerifier2D(BNESolverContext<Double[], Double[]> context) {
		super();
		this.context = context;
	}


	public Strategy<Double[], Double[]> convertStrategy(int nPoints, Strategy<Double[], Double[]> s) {
		return new VerificationStrategy(nPoints, 0.6, s);
		//return new QuadraticGridStrategy2D((int) (nPoints * s.getMaxValue()[0]), 0.5, s);
	}
	
	
	@Override
	public double computeEpsilon(int gridsize, int i, Strategy<Double[], Double[]> si, List<Strategy<Double[], Double[]>> s) {	
		//if (i>3) gridsize *= 2;
		
		double cellEpsilon2D = 0.0;
		double boundaryEpsilon1D = 0.0;
		double boundaryEpsilon0D = 0.0;
		
		Map<Integer, Map<Integer, Optimizer.Result<Double[]>>> results = new HashMap<>();
		

		VerificationStrategy sGrid = (VerificationStrategy) s.get(i);
		
		for (int j=0; j<gridsize; j++) {
			results.put(j, new HashMap<>());
			for (int k=0; k<gridsize; k++) {
				
				Double[] value = sGrid.getGridpoint(new Integer[]{j, k});
				
				Double[] equilibriumBid = si.getBid(value);
				Optimizer.Result<Double[]> result = context.optimizer.findBR(i, value, equilibriumBid, s);
				results.get(j).put(k, result);

				// compute epsilon bound on each 2d cell, then on the 1d boundaries, 
				// then on the 0d boundary (i.e. topmost grid point)
				double pointEpsilon = UtilityHelpers.absoluteLoss(result.oldutility, result.utility);
				if (j>0 && k>0) {
					double epsilon = result.utility - results.get(j-1).get(k-1).utility + pointEpsilon;
					cellEpsilon2D = Math.max(cellEpsilon2D, epsilon);
				}
				if (j==gridsize-1 && k>0) {
					double epsilon = result.utility - results.get(j).get(k-1).utility + pointEpsilon;
					boundaryEpsilon1D = Math.max(boundaryEpsilon1D, epsilon);
				}
				if (j>0 && k==gridsize-1) {
					double epsilon = result.utility - results.get(j-1).get(k).utility + pointEpsilon;
					boundaryEpsilon1D = Math.max(boundaryEpsilon1D, epsilon);
				}
				if (j==gridsize-1 && k==gridsize-1) {
					boundaryEpsilon0D = pointEpsilon;
				}
			}
		}
		
		return Math.max(Math.max(cellEpsilon2D, boundaryEpsilon1D), boundaryEpsilon0D);
	}

}
