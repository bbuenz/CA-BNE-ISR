package ch.uzh.ifi.ce.cabne.BR;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

import ch.uzh.ifi.ce.cabne.Helpers;
import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.pointwiseBR.Optimizer;
import ch.uzh.ifi.ce.cabne.strategy.ConstantGridStrategy2D;
import ch.uzh.ifi.ce.cabne.strategy.Strategy;

public class ExactGrid2DVerifier implements Verifier<Double[], Double[]> {
	BNESolverContext<Double[], Double[]> context;
	

	public ExactGrid2DVerifier(BNESolverContext<Double[], Double[]> context) {
		super();
		this.context = context;
	}


	public Strategy<Double[], Double[]> convertStrategy(int nPoints, Strategy<Double[], Double[]> s) {
		Double[] maxValue = s.getMaxValue();

		double[][] left = new double[nPoints][];
		double[][] right = new double[nPoints][];
		for (int j=0; j<nPoints; j++) {
			left[j] = new double[nPoints];
			right[j] = new double[nPoints];
			for (int k=0; k<nPoints; k++) {
				Double[] value = new Double[]{
						maxValue[0] * ((double) j) / (nPoints),
						maxValue[1] * ((double) k) / (nPoints)
						};
				Double[] bid = s.getBid(value);
				left[j][k] = bid[0];
				right[j][k] = bid[1];
			}
		}
		RealMatrix mLeft = MatrixUtils.createRealMatrix(left);
		RealMatrix mRight = MatrixUtils.createRealMatrix(right);
		return new ConstantGridStrategy2D(mLeft, mRight, maxValue[0], maxValue[1]);

	}
	
	
	@Override
	public double computeEpsilon(int gridsize, int i, Strategy<Double[], Double[]> si, List<Strategy<Double[], Double[]>> s) {	
		Double[] maxValue = si.getMaxValue();
		
		double cellEpsilon2D = 0.0;
		double boundaryEpsilon1D = 0.0;
		double boundaryEpsilon0D = 0.0;
		
		Map<Integer, Map<Integer, Optimizer.Result<Double[]>>> results = new HashMap<>();
		
		for (int j=0; j<gridsize; j++) {
			results.put(j, new HashMap<>());
			for (int k=0; k<gridsize; k++) {
				Double[] value = new Double[]{
						maxValue[0] * ((double) j) / (gridsize),
						maxValue[1] * ((double) k) / (gridsize)
						};
				
				// NOTE: it's extremely important that the strategy lookup of the equilibrium bid is done using s and
				// not sPWC.
				// Grid strategies can have tiny numerical errors, and if we look up the exact bottom-left point of a 
				// segment, the lookup might miss and return the next lower bid level, which is significantly lower, 
				// thus amplifying the numerical error by orders of magnitude.
				Double[] equilibriumBid = s.get(i).getBid(value);
				Optimizer.Result<Double[]> result = context.optimizer.findBR(i, value, equilibriumBid, s);
				results.get(j).put(k, result);

				// compute epsilon bound on each 2d cell, then on the 1d boundaries, 
				// then on the 0d boundary (i.e. topmost grid point)
				double pointEpsilon = Helpers.absoluteUtilityLoss(result.oldutility, result.utility);
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