package ch.uzh.ifi.ce.cabne.pointwiseBR;

import java.util.ArrayList;
import java.util.List;


// a pattern consisting of a cross combined with a smaller diagonal cross
// the diagonal cross is smaller (sqrt(2) distance from the center), to make sure all points lie on a grid and can get reused.
// TODO: implement for arbitrary dimensions


public class DoubleCrossPattern2D extends Pattern<Double[]> {
	
	public DoubleCrossPattern2D() {
		super(2);
	}

	@Override
	List<Double[]> getPatternPoints(Double[] center, int npoints, double scale) {
		if (npoints != 9) throw new RuntimeException();
		
		//System.out.format("%9.6f  %9.6f (center)\n", center[0], center[1]);
		List<Double[]> result = new ArrayList<>(npoints);
		for (int x=-1; x<=1; x++) {
			for (int y=-1; y<=1; y++) {
				Double[] nextPoint = new Double[]{
					Math.max(center[0] + x*scale*(y==0 ? 1.0 : 0.5), 0.0),
					Math.max(center[1] + y*scale*(x==0 ? 1.0 : 0.5), 0.0)
				};
				result.add(nextPoint);					
				//System.out.format("%9.6f  %9.6f\n", nextPoint[0], nextPoint[1]);
			}
		}
		//System.out.println();
		return result;
	}

	@Override
	int getCenterIndex(int npoints) {
		return 4;
	}
	
	@Override
	protected String bidHash(Double[] key) {
		StringBuilder builder = new StringBuilder();
		for (int x=0; x<key.length; x++) {
			builder.append(String.format("%9.6f|", key[x]));
		}
		return builder.toString();
	}

}
