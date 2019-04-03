package ch.uzh.ifi.ce.cabne.domains.LLLLGG;


public class LLLLGGPayAsBid extends LLLLGGMechanism {

	@Override
	public double computeUtility(int i, Double[] v, Double[][] bids, int[] alloc) {
		double utility = 0.0;
		for (int bundle : alloc) {
			if (bundle/2 == i) utility += v[bundle%2] - bids[i][bundle%2];
		}
		return utility;
	}

	@Override
	public double[] computePayments(Double[][] bids, int[] alloc) {
		double[] payments=new double[bids.length];
		for (int bundle : alloc) {
			payments[bundle/2]+=bids[bundle/2][bundle%2];
		}
		return payments;
	}
}
